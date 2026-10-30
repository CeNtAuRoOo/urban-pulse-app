package es.grupo18.urbanpulse;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import es.grupo18.urbanpulse.config.*;
import es.grupo18.urbanpulse.dto.auth.*;
import es.grupo18.urbanpulse.exception.ApiException;
import es.grupo18.urbanpulse.repository.*;
import es.grupo18.urbanpulse.security.InMemoryRateLimiter;
import es.grupo18.urbanpulse.service.*;
import es.grupo18.urbanpulse.service.mail.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:auth-test.properties")
@Import(EmailVerificationPostgresIT.BusinessProbe.class)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class EmailVerificationPostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    AuthService auth;
    @Autowired
    EmailVerificationService verification;
    @Autowired
    EmailVerificationRepository requests;
    @Autowired
    UserRepository users;
    @Autowired
    EmailVerificationCleanup cleanup;
    @Autowired
    EmailVerificationProperties settings;
    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper mapper;
    @MockitoBean
    EmailSender sender;
    @MockitoBean
    InMemoryRateLimiter limiter;
    @MockitoBean
    Clock clock;
    @MockitoSpyBean
    EmailVerificationCodeService codes;
    private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();
    private final String registration = "{\"displayName\":\"Ciudadana\",\"email\":\" PERSON@EXAMPLE.ORG \",\"password\":\"password-123\"}";

    @BeforeEach
    void setup() {
        jdbc.update("delete from email_verifications");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        messages.clear();
        when(clock.instant()).thenAnswer(invocation -> Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(limiter.acquire(anyString(), any())).thenReturn(new InMemoryRateLimiter.Decision(true, 0));
        doAnswer(invocation -> {
            EmailMessage message = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            // Otra conexión comprueba visibilidad del commit; no reutiliza recursos de la
            // transacción del alta.
            try (var connection = dataSource.getConnection();
                    var query = connection.prepareStatement("""
                            select count(*) from users u join email_verifications v on v.user_id=u.id
                            join user_roles r on r.user_id=u.id and r.role_code='CITIZEN' where u.email=?
                            """)) {
                query.setString(1, message.to());
                try (var result = query.executeQuery()) {
                    result.next();
                    assertThat(result.getInt(1)).isEqualTo(1);
                }
            }
            messages.add(message);
            return null;
        }).when(sender).send(any());
    }

    private TokenDTO register() {
        return auth.register(new RegisterRequest("Ciudadana", "person@example.org", "password-123"));
    }

    private String code() {
        var match = Pattern.compile("[0-9]{8}").matcher(messages.getLast().text());
        assertThat(match.find()).isTrue();
        return match.group();
    }

    private void confirmHttp(TokenDTO token, String code, int status) throws Exception {
        mvc.perform(post("/api/v1/auth/email-verifications/confirm")
                .header("Authorization", "Bearer " + token.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().is(status));
    }

    @Test
    void registrationCommitsUserRoleAndHmacBeforeSendingAndRollbackNeverSends() throws Exception {
        doReturn("01234567").when(codes).generate();
        var response = mvc
                .perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.user.roles[0]").value("CITIZEN")).andReturn().getResponse().getContentAsString();
        TokenDTO token = mapper.readValue(response, TokenDTO.class);
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().to()).isEqualTo("person@example.org");
        assertThat(code()).isEqualTo("01234567");
        var saved = requests.findById(token.user().id()).orElseThrow();
        assertThat(codes.matches(token.user().id(), code(), saved.getCodeHash())).isTrue();
        assertThat(saved.getCodeHash()).hasSize(32);
        assertThat(response).doesNotContain("01234567", "codeHash", "passwordHash", "tokenVersion");
        assertThat(java.util.HexFormat.of().formatHex(saved.getCodeHash())).doesNotContain("3031323334353637");
        int before = messages.size();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            auth.register(new RegisterRequest("Rollback", "rollback@example.org", "password-123"));
            assertThat(messages).hasSize(before);
            status.setRollbackOnly();
        });
        assertThat(messages).hasSize(before);
        assertThat(users.findByEmail("rollback@example.org")).isEmpty();
        assertThat(requests.count()).isEqualTo(1);
    }

    @Test
    void smtpFailureDoesNotTurnCommittedRegistrationOrResendInto500(CapturedOutput output) throws Exception {
        doThrow(new MailSendException("PRIVATE_SMTP password=PRIVATE_SECRET person@example.org")).when(sender)
                .send(any());
        var response = mvc
                .perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        TokenDTO token = mapper.readValue(response, TokenDTO.class);
        byte[] previous = requests.findById(token.user().id()).orElseThrow().getCodeHash();
        mvc.perform(post("/api/v1/auth/email-verifications/resend").header("Authorization",
                "Bearer " + token.accessToken()))
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        assertThat(requests.findById(token.user().id()).orElseThrow().getCodeHash()).isNotEqualTo(previous);
        assertThat(auth.login(new LoginRequest("person@example.org", "password-123")).user().emailVerified()).isFalse();
        assertThat(output.getAll()).contains("event=VERIFICATION_EMAIL_FAILED")
                .doesNotContain("PRIVATE_SMTP", "PRIVATE_SECRET", token.accessToken(), "person@example.org");
    }

    @Test
    void confirmationChangesAuthorizationOnNextReadUsingTheSameJwtAndIsIdempotent() throws Exception {
        TokenDTO token = register();
        String bearer = "Bearer " + token.accessToken();
        mvc.perform(get("/_test/verified-business").header("Authorization", bearer))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.org\",\"password\":\"password-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.emailVerified").value(false));
        String good = code();
        String wrong = good.equals("00000000") ? "99999999" : "00000000";
        mvc.perform(post("/api/v1/auth/email-verifications/confirm").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + wrong + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        confirmHttp(token, good, 204);
        assertThat(users.findById(token.user().id()).orElseThrow().isEmailVerified()).isTrue();
        assertThat(users.findById(token.user().id()).orElseThrow().getTokenVersion()).isZero();
        assertThat(requests.findById(token.user().id())).isEmpty();
        mvc.perform(get("/_test/verified-business").header("Authorization", bearer)).andExpect(status().isOk());
        confirmHttp(token, good, 204);
        mvc.perform(post("/api/v1/auth/email-verifications/resend").header("Authorization", bearer))
                .andExpect(status().isNoContent());
        assertThat(messages).hasSize(1);
        assertThat(auth.login(new LoginRequest("person@example.org", "password-123")).user().emailVerified()).isTrue();
    }

    @Test
    void resendReplacesCodeAndInvalidOrExpiredOrMissingCodesShare422() throws Exception {
        // Incluso una colisión aleatoria debe sustituir realmente el código anterior.
        doReturn("01234567", "01234567", "87654321").when(codes).generate();
        TokenDTO token = register();
        String old = code();
        mvc.perform(post("/api/v1/auth/email-verifications/resend").header("Authorization",
                "Bearer " + token.accessToken()))
                .andExpect(status().isAccepted());
        assertThat(requests.count()).isEqualTo(1);
        confirmHttp(token, old, 422);
        String fresh = code();
        Instant expiry = requests.findById(token.user().id()).orElseThrow().getExpiresAt();
        when(clock.instant()).thenReturn(expiry);
        confirmHttp(token, fresh, 422);
        requests.deleteById(token.user().id());
        confirmHttp(token, fresh, 422);
        assertThat(users.findById(token.user().id()).orElseThrow().isEmailVerified()).isFalse();
        assertThat(messages).hasSize(2);
    }

    @Test
    void effectiveCodeExpiryNeverExtendsAccountLifetimeAndResendAtDeadlineIsGone() throws Exception {
        TokenDTO token = register();
        Instant deadline = users.findById(token.user().id()).orElseThrow().getCreatedAt().plus(Duration.ofHours(24));
        when(clock.instant()).thenReturn(deadline.minusSeconds(30));
        assertThat(verification.resend(token.user().id(), 0)).isTrue();
        assertThat(requests.findById(token.user().id()).orElseThrow().getExpiresAt()).isEqualTo(deadline);
        when(clock.instant()).thenReturn(deadline);
        TokenDTO renewed = auth.login(new LoginRequest("person@example.org", "password-123"));
        mvc.perform(post("/api/v1/auth/email-verifications/resend").header("Authorization",
                "Bearer " + renewed.accessToken()))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("VERIFICATION_ACCOUNT_EXPIRED"));
        confirmHttp(renewed, code(), 422);
        assertThat(messages).hasSize(2);
    }

    @Test
    void cleanupIsBatchedPreservesVerifiedAndFreshUsersAndReleasesEmails() {
        Instant now = Instant.now();
        when(clock.instant()).thenReturn(now);
        // Datos sintéticos SQL para probar lotes sin gastar BCrypt en cada fila.
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    insert into users(id,display_name,email,password_hash,created_at,updated_at,email_verified)
                    values(?,?,?, ?,?,?,?)
                    """, id, "Cleanup", "cleanup" + i + "@example.org", "x".repeat(60),
                    java.sql.Timestamp.from(now.minus(Duration.ofHours(i == 4 ? 1 : 25))),
                    java.sql.Timestamp.from(now), i == 3);
            jdbc.update("insert into user_roles values (?, 'CITIZEN')", id);
            jdbc.update("insert into email_verifications values (?, ?, ?, ?)", id, new byte[32],
                    java.sql.Timestamp.from(now.minusSeconds(1200)), java.sql.Timestamp.from(now.minusSeconds(600)));
        }
        var bounded = new EmailVerificationCleanup(jdbc, manager,
                new EmailVerificationProperties(settings.secret(), 2, 1), clock);
        assertThat(bounded.clean()).isEqualTo(new EmailVerificationCleanup.Result(2, 2));
        assertThat(users.count()).isEqualTo(3);
        assertThat(bounded.clean().users()).isEqualTo(1);
        cleanup.clean();
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findByEmail("cleanup3@example.org").orElseThrow().isEmailVerified()).isTrue();
        assertThat(users.findByEmail("cleanup4@example.org")).isPresent();
        assertThat(requests.count()).isZero();
        assertThat(auth.register(new RegisterRequest("New owner", "cleanup0@example.org", "password-123")).user()
                .emailVerified())
                .isFalse();
        assertThat(cleanup.clean()).isEqualTo(new EmailVerificationCleanup.Result(0, 0));
    }

    @Test
    void concurrentResendWaitsForConfirmationAndCannotLeaveARequestForVerifiedUser() throws Exception {
        TokenDTO token = register();
        String valid = code();
        CountDownLatch confirmed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> confirm = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                verification.confirm(token.user().id(), 0, valid);
                confirmed.countDown();
                await(release);
            }));
            assertThat(confirmed.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> resend = executor.submit(() -> verification.resend(token.user().id(), 0));
            try {
                assertThatThrownBy(() -> resend.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            confirm.get(10, TimeUnit.SECONDS);
            assertThat(resend.get(10, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(requests.findById(token.user().id())).isEmpty();
        assertThat(messages).hasSize(1);
    }

    @Test
    void confirmationAfterConcurrentResendCannotConsumeOldCode() throws Exception {
        TokenDTO token = register();
        String old = code();
        CountDownLatch replaced = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> resend = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                verification.resend(token.user().id(), 0);
                replaced.countDown();
                await(release);
            }));
            assertThat(replaced.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> confirm = executor.submit(() -> verification.confirm(token.user().id(), 0, old));
            try {
                assertThatThrownBy(() -> confirm.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            resend.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> confirm.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ApiException.class);
        }
        assertThat(users.findById(token.user().id()).orElseThrow().isEmailVerified()).isFalse();
        verification.confirm(token.user().id(), 0, code());
        assertThat(requests.findById(token.user().id())).isEmpty();
    }

    @Test
    void cleanupSkipsConfirmationLockThenPreservesTheCommittedVerifiedAccount() throws Exception {
        TokenDTO token = register();
        Instant deadline = users.findById(token.user().id()).orElseThrow().getCreatedAt().plus(Duration.ofHours(24));
        when(clock.instant()).thenReturn(deadline.minusSeconds(1));
        verification.resend(token.user().id(), 0);
        String valid = code();
        CountDownLatch confirmed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> confirm = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                verification.confirm(token.user().id(), 0, valid);
                confirmed.countDown();
                await(release);
            }));
            assertThat(confirmed.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                when(clock.instant()).thenReturn(deadline.plusSeconds(1));
                assertThat(cleanup.clean().users()).isZero();
            } finally {
                release.countDown();
            }
            confirm.get(10, TimeUnit.SECONDS);
        }
        assertThat(cleanup.clean().users()).isZero();
        assertThat(users.findById(token.user().id()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void cleanupFailureLogsOnlyTypeAndCanRetry(CapturedOutput output) {
        var brokenJdbc = mock(JdbcTemplate.class);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("PRIVATE_DB_CREDENTIAL"))
                .when(brokenJdbc).update(anyString(), any(Object[].class));
        var broken = new EmailVerificationCleanup(brokenJdbc, manager, settings, clock);
        broken.scheduledCleanup();
        assertThat(output.getAll()).contains("event=VERIFICATION_CLEANUP_FAILED")
                .doesNotContain("PRIVATE_DB_CREDENTIAL");
        assertThat(cleanup.clean()).isEqualTo(new EmailVerificationCleanup.Result(0, 0));
    }

    @Test
    void verificationSqlEnforcesHashLengthLifetimeUniqueUserAndForeignKey() {
        TokenDTO token = register();
        UUID id = token.user().id();
        assertThatThrownBy(
                () -> jdbc.update("update email_verifications set code_hash=? where user_id=?", new byte[31], id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(
                () -> jdbc.update("update email_verifications set expires_at=created_at where user_id=?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update email_verifications set expires_at=created_at + interval '11 minutes' where user_id=?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc
                .update("insert into email_verifications select * from email_verifications where user_id=?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(
                () -> jdbc.update("insert into email_verifications values (?, ?, now(), now() + interval '1 minute')",
                        UUID.randomUUID(), new byte[32]))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void confirmationRollbackRestoresBothFlagAndCodeAndConcurrentRepeatsAreIdempotent() throws Exception {
        TokenDTO token = register();
        String valid = code();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            verification.confirm(token.user().id(), 0, valid);
            status.setRollbackOnly();
        });
        assertThat(users.findById(token.user().id()).orElseThrow().isEmailVerified()).isFalse();
        assertThat(requests.findById(token.user().id())).isPresent();
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Runnable confirm = () -> {
                await(start);
                verification.confirm(token.user().id(), 0, valid);
            };
            Future<?> first = executor.submit(confirm);
            Future<?> second = executor.submit(confirm);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(users.findById(token.user().id()).orElseThrow().isEmailVerified()).isTrue();
        assertThat(users.findById(token.user().id()).orElseThrow().getTokenVersion()).isZero();
        assertThat(requests.findById(token.user().id())).isEmpty();
        assertThat(messages).hasSize(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Timeout esperando al test");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @RestController
    static class BusinessProbe {
        @GetMapping("/_test/verified-business")
        String business() {
            return "ok";
        }
    }
}
