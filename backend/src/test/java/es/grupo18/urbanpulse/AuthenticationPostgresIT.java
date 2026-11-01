package es.grupo18.urbanpulse;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import es.grupo18.urbanpulse.dto.auth.*;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.exception.ApiException;
import es.grupo18.urbanpulse.repository.*;
import es.grupo18.urbanpulse.security.*;
import es.grupo18.urbanpulse.service.AuthService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class AuthenticationPostgresIT {
    // Always creates a new disposable PostgreSQL. No environment-supplied database
    // URL.
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
    UserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwords;
    @Autowired
    JwtDecoder decoder;
    @Autowired
    JwtEncoder encoder;
    @Autowired
    DatabaseJwtAuthenticationConverter converter;
    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    MockMvc mvc;
    @MockitoBean
    InMemoryRateLimiter limiter;
    @MockitoBean
    es.grupo18.urbanpulse.service.mail.EmailSender emailSender;

    @BeforeEach
    void clearAccounts() {
        jdbc.update("delete from email_verifications");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        when(limiter.acquire(anyString(), any())).thenReturn(new InMemoryRateLimiter.Decision(true, 0));
    }

    private TokenDTO register(String email) {
        return auth.register(new RegisterRequest("Ciudadana", email, "password-123"));
    }

    @Test
    void flywaySchemaValidatesAndPersistsV7DefaultsAndCompositeRoles() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success and version='1'", Integer.class))
                .isEqualTo(1);
        assertThat(roles.findAll()).hasSize(5);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name='roles' and column_name='scope'",
                Integer.class)).isZero();
        TokenDTO token = register(" PERSON@EXAMPLE.ORG ");
        UUID id = token.user().id();
        assertThat(id.version()).isEqualTo(7);
        var user = users.findByEmail("person@example.org").orElseThrow();
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.globalRoles()).containsExactly(RoleCode.CITIZEN);
        assertThat(user.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
        assertThat(passwords.matches("password-123", user.getPasswordHash())).isTrue();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getTokenVersion()).isZero();
        assertThat(user.getPreferences().isInApp()).isTrue();
        assertThat(user.getPreferences().isEmail()).isTrue();
        assertThat(user.getPreferences().isPush()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id=?", Integer.class, id))
                .isEqualTo(1);
        UUID sqlId = UUID.randomUUID();
        jdbc.update(
                "insert into users(id,display_name,email,password_hash,created_at,updated_at) values(?,?,?,?,now(),now())",
                sqlId, "SQL defaults", "defaults@example.org", user.getPasswordHash());
        var defaults = users.findById(sqlId).orElseThrow();
        assertThat(defaults.isActive()).isTrue();
        assertThat(defaults.isEmailVerified()).isFalse();
        assertThat(defaults.getTokenVersion()).isZero();
        assertThat(defaults.getPreferences().isInApp() && defaults.getPreferences().isEmail()
                && defaults.getPreferences().isPush()).isTrue();
    }

    @Test
    void enforcesEmailFkCompositeKeyAndGlobalRoleChecks() {
        TokenDTO token = register("person@example.org");
        UUID id = token.user().id();
        assertThatThrownBy(() -> register(" PERSON@EXAMPLE.ORG ")).isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThatThrownBy(() -> jdbc.update("update users set email='PERSON@EXAMPLE.ORG' where id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into user_roles values (?, 'CITIZEN')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into user_roles values (?, 'TECHNICIAN')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into user_roles values (?, 'ADMIN')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into user_roles values (?, 'UNKNOWN')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void registrationRollsBackIfBaseRoleCannotBeAssigned() {
        jdbc.update("delete from roles where code='CITIZEN'");
        try {
            assertThatThrownBy(() -> register("rollback@example.org")).isInstanceOf(NoSuchElementException.class);
            assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
        } finally {
            jdbc.update("insert into roles values ('CITIZEN', 'Ciudadanía: rol base de todas las cuentas')");
        }
    }

    @Test
    void concurrentRegistrationHasOneSuccessAndOneConflict() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> attempt = () -> {
                start.await();
                try {
                    register("race@example.org");
                    return 201;
                } catch (ApiException ex) {
                    return ex.status().value();
                }
            };
            Future<Integer> first = executor.submit(attempt);
            Future<Integer> second = executor.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from user_roles", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void concurrentPasswordChangesWaitForRowAndCannotLoseRevocation() throws Exception {
        TokenDTO token = register("race@example.org");
        UUID id = token.user().id();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(3)) {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                users.findForPasswordChange(id).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("Lock test timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Callable<Integer> change = () -> {
                started.countDown();
                try {
                    auth.changePassword(id, 0, new ChangePasswordRequest("password-123", "password-456"));
                    return 204;
                } catch (ApiException ex) {
                    return ex.status().value();
                }
            };
            Future<Integer> first = executor.submit(change);
            Future<Integer> second = executor.submit(change);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> first.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 401);
            assertThat(users.findById(id).orElseThrow().getTokenVersion()).isEqualTo(1);
            assertThatThrownBy(() -> converter.convert(decoder.decode(token.accessToken())))
                    .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
            assertThat(auth.login(new LoginRequest("race@example.org", "password-456")).user().id()).isEqualTo(id);
        }
    }

    @Test
    void fullHttpLifecycleInvalidatesEveryPreviouslyIssuedToken() throws Exception {
        String registration = "{\"displayName\":\"Ciudadana\",\"email\":\"person@example.org\",\"password\":\"password-123\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.emailVerified").value(false));
        TokenDTO first = auth.login(new LoginRequest("person@example.org", "password-123"));
        TokenDTO second = auth.login(new LoginRequest("person@example.org", "password-123"));
        // Distinct valid JWT from an earlier session, independent of wall-clock test
        // speed.
        Instant earlier = Instant.now().minusSeconds(60);
        String older = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("urbanpulse-tests").subject(first.user().id().toString())
                        .issuedAt(earlier).expiresAt(earlier.plusSeconds(7200)).claim("ver", 0).build()))
                .getTokenValue();
        assertThat(older).isNotEqualTo(first.accessToken());
        assertThat(converter.convert(decoder.decode(older)).isAuthenticated()).isTrue();
        String change = "{\"currentPassword\":\"password-123\",\"newPassword\":\"password-456\"}";
        mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", "Bearer " + first.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        for (String token : List.of(first.accessToken(), second.accessToken(), older)) {
            mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(change))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.org\",\"password\":\"password-456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(7200));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"person@example.org\",\"password\":\"password-123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removingCitizenInPostgresRejectsHttpAuthenticationWithoutRepairingRoles() throws Exception {
        TokenDTO token = register("person@example.org");
        UUID id = token.user().id();
        jdbc.update("insert into user_roles values (?, 'ADMIN')", id);
        jdbc.update("delete from user_roles where user_id=? and role_code='CITIZEN'", id);
        for (int remainingRoles : List.of(1, 0)) {
            mvc.perform(post("/api/v1/users/me/password-changes")
                    .header("Authorization", "Bearer " + token.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"password-123\",\"newPassword\":\"password-456\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            var user = users.findById(id).orElseThrow();
            assertThat(user.isActive()).isTrue();
            assertThat(user.getTokenVersion()).isZero();
            assertThat(passwords.matches("password-123", user.getPasswordHash())).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from user_roles where user_id=?", Integer.class, id))
                    .isEqualTo(remainingRoles);
            jdbc.update("delete from user_roles where user_id=?", id);
        }
        verifyNoInteractions(limiter);
    }

    @Test
    void permissionsAndDeactivationTakeEffectOnTheNextDatabaseRead() {
        TokenDTO token = register("person@example.org");
        var jwt = decoder.decode(token.accessToken());
        jdbc.update("insert into user_roles values (?, 'ADMIN')", token.user().id());
        assertThat(converter.convert(jwt).getAuthorities()).extracting("authority").contains("ROLE_ADMIN",
                "ROLE_CITIZEN");
        jdbc.update("delete from user_roles where user_id=? and role_code='ADMIN'", token.user().id());
        assertThat(converter.convert(jwt).getAuthorities()).extracting("authority").containsExactly("ROLE_CITIZEN");
        jdbc.update("update users set active=false where id=?", token.user().id());
        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
        assertThatThrownBy(() -> auth.login(new LoginRequest("person@example.org", "password-123")))
                .isInstanceOf(ApiException.class);
    }
}
