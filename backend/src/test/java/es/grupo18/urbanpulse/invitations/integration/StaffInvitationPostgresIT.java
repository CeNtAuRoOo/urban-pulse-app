package es.grupo18.urbanpulse.invitations.integration;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.auth.dto.RegisterRequest;
import es.grupo18.urbanpulse.auth.service.AuthService;
import es.grupo18.urbanpulse.auth.service.EmailVerificationCleanup;
import es.grupo18.urbanpulse.auth.service.EmailVerificationService;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.invitations.dto.DepartmentInvitationRequest;
import es.grupo18.urbanpulse.invitations.dto.GlobalInvitationRequest;
import es.grupo18.urbanpulse.invitations.dto.InvitationDTO;
import es.grupo18.urbanpulse.invitations.dto.InvitationQuery;
import es.grupo18.urbanpulse.invitations.dto.InvitationRequest;
import es.grupo18.urbanpulse.invitations.model.InvitationStatus;
import es.grupo18.urbanpulse.invitations.repository.StaffInvitationRepository;
import es.grupo18.urbanpulse.invitations.service.InvitationCodeService;
import es.grupo18.urbanpulse.invitations.service.StaffInvitationService;
import es.grupo18.urbanpulse.organization.dto.CreateDepartmentRequest;
import es.grupo18.urbanpulse.organization.dto.PatchDepartmentRequest;
import es.grupo18.urbanpulse.organization.repository.DepartmentMembershipRepository;
import es.grupo18.urbanpulse.organization.repository.DepartmentRepository;
import es.grupo18.urbanpulse.organization.service.DepartmentService;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.mail.EmailMessage;
import es.grupo18.urbanpulse.shared.mail.EmailSender;
import es.grupo18.urbanpulse.shared.ratelimit.InMemoryRateLimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class StaffInvitationPostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    StaffInvitationService service;
    @Autowired
    StaffInvitationRepository invitations;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    DepartmentService departmentService;
    @Autowired
    DepartmentMembershipRepository memberships;
    @Autowired
    UserRepository users;
    @Autowired
    AuthService auth;
    @Autowired
    EmailVerificationService verification;
    @Autowired
    EmailVerificationCleanup cleanup;
    @Autowired
    InvitationCodeService codes;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DataSource dataSource;
    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    MockMvc mvc;
    @MockitoBean
    EmailSender sender;
    @MockitoBean
    Clock clock;
    @MockitoBean
    InMemoryRateLimiter limiter;
    @MockitoSpyBean
    AuditEventRepository audit;
    private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();
    private UUID admin;
    private UUID citizen;
    private UUID department;
    private volatile Instant now;

    @BeforeEach
    void setup() {
        jdbc.execute("truncate audit_events, staff_invitations, department_memberships, departments, "
                + "email_verifications, user_roles, users");
        now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        when(clock.instant()).thenAnswer(invocation -> now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(limiter.acquire(anyString(), any())).thenReturn(new InMemoryRateLimiter.Decision(true, 0));
        admin = fixture("admin@example.org", true, true);
        citizen = fixture("person@example.org", true, false);
        department = departmentService
                .create(new CreateDepartmentRequest("Movilidad <urbana>", "MOV", "Fixture aislado", true)).id();
        messages.clear();
        doAnswer(invocation -> {
            EmailMessage message = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (message.subject().startsWith("Invitación")) {
                try (var connection = dataSource.getConnection();
                        var query = connection.prepareStatement(
                                "select count(*) from staff_invitations i join audit_events a "
                                        + "on a.resource_id=i.id::text where i.email=? and a.action='STAFF_INVITATION_CREATED'")) {
                    query.setString(1, message.to());
                    try (var rows = query.executeQuery()) {
                        rows.next();
                        assertThat(rows.getInt(1)).isGreaterThan(0);
                    }
                }
            }
            messages.add(message);
            return null;
        }).when(sender).send(any());
    }

    private UUID fixture(String email, boolean verified, boolean administrator) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users(id,display_name,email,password_hash,email_verified,created_at,updated_at)
                values(?, 'Fixture', ?, ?, ?, ?, ?)
                """, id, email, "x".repeat(60), verified, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("insert into user_roles values (?, 'CITIZEN')", id);
        if (administrator)
            jdbc.update("insert into user_roles values (?, 'ADMIN')", id);
        return id;
    }

    private InvitationDTO issue(RoleCode role) {
        return issue("person@example.org", role);
    }

    private InvitationDTO issue(String email, RoleCode role) {
        InvitationRequest request = role.isGlobal() ? new GlobalInvitationRequest(email, role)
                : new DepartmentInvitationRequest(email, role, department);
        return service.create(admin, 0, request);
    }

    private String code() {
        var match = Pattern.compile("Código: ([A-Za-z0-9_-]{43})").matcher(messages.getLast().text());
        assertThat(match.find()).isTrue();
        return match.group(1);
    }

    private void fails(int status, String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.status().value()).isEqualTo(status);
            assertThat(ex.code()).isEqualTo(code);
        });
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = { "ADMIN", "OPERATOR", "TECHNICIAN", "DEPARTMENT_COORDINATOR" })
    void issuesAndRedeemsExactlyOnePermissionWithTransactionalAudit(RoleCode role) {
        var dto = issue(role);
        String secret = code();
        assertThat(dto.id().version()).isEqualTo(7);
        assertThat(dto.createdAt()).isEqualTo(now);
        assertThat(dto.expiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
        assertThat(users.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select code_hash from staff_invitations where id=?", byte[].class, dto.id()))
                .isEqualTo(codes.hash(secret));
        String password = users.findById(citizen).orElseThrow().getPasswordHash();
        var result = service.redeem(citizen, 0, secret);
        assertThat(result.role()).isEqualTo(role);
        assertThat(service.get(admin, 0, dto.id()).status()).isEqualTo(InvitationStatus.REDEEMED);
        assertThat(service.get(admin, 0, dto.id()).redeemedBy()).isEqualTo(citizen);
        assertThat(users.findByEmail("person@example.org").orElseThrow().globalRoles()).contains(RoleCode.CITIZEN);
        assertThat(users.findById(citizen).orElseThrow().getPasswordHash()).isEqualTo(password);
        assertThat(users.count()).isEqualTo(2);
        assertThat(audit.count()).isEqualTo(2);
        assertThat(jdbc.queryForList("select details::text from audit_events", String.class))
                .allSatisfy(details -> assertThat(details).doesNotContain(secret, "codeHash", "person@example.org"));
        if (role.isDepartmental()) {
            assertThat(memberships.count()).isEqualTo(1);
            assertThat(memberships.findAll().getFirst().getId().version()).isEqualTo(7);
        }
    }

    @Test
    void permissionAccumulationKeepsExistingRolesAndDifferentDepartmentMemberships() {
        issue(RoleCode.OPERATOR);
        service.redeem(citizen, 0, code());
        issue(RoleCode.TECHNICIAN);
        service.redeem(citizen, 0, code());
        department = departmentService.create(new CreateDepartmentRequest("Otra unidad", "OTRA", null, true)).id();
        issue(RoleCode.TECHNICIAN);
        service.redeem(citizen, 0, code());
        assertThat(memberships.count()).isEqualTo(2);
        assertThat(users.findByEmail("person@example.org").orElseThrow().globalRoles())
                .containsExactlyInAnyOrder(RoleCode.CITIZEN, RoleCode.OPERATOR);
    }

    @Test
    void departmentMustExistAndRemainActiveAndCitizenCannotBeInvited() {
        fails(422, "VALIDATION_ERROR", () -> issue(RoleCode.CITIZEN));
        fails(422, "VALIDATION_ERROR", () -> service.create(admin, 0,
                new DepartmentInvitationRequest("person@example.org", RoleCode.TECHNICIAN, null)));
        fails(422, "INVALID_DEPARTMENT", () -> service.create(admin, 0,
                new DepartmentInvitationRequest("person@example.org", RoleCode.TECHNICIAN, UUID.randomUUID())));
        var dto = issue(RoleCode.TECHNICIAN);
        String secret = code();
        departmentService.patch(department, new PatchDepartmentRequest(null, null, null, false));
        fails(422, "INVALID_DEPARTMENT", () -> issue(RoleCode.TECHNICIAN));
        fails(422, "INVALID_DEPARTMENT", () -> service.redeem(citizen, 0, secret));
        assertThat(service.get(admin, 0, dto.id()).status()).isEqualTo(InvitationStatus.ACTIVE);
        assertThat(memberships.count()).isZero();
    }

    @Test
    void departmentAdministrationPreservesMembershipsAndInvitationHistory() {
        var redeemed = issue(RoleCode.TECHNICIAN);
        service.redeem(citizen, 0, code());
        var pending = issue(RoleCode.DEPARTMENT_COORDINATOR);
        String secret = code();
        departmentService.patch(department, new PatchDepartmentRequest("Movilidad municipal", "MUNIC", null, false));
        assertThat(memberships.count()).isEqualTo(1);
        assertThat(memberships.findAll().getFirst().getLeftAt()).isNull();
        assertThat(service.get(admin, 0, redeemed.id()).status()).isEqualTo(InvitationStatus.REDEEMED);
        fails(422, "INVALID_DEPARTMENT", () -> service.redeem(citizen, 0, secret));
        assertThat(service.get(admin, 0, pending.id()).status()).isEqualTo(InvitationStatus.ACTIVE);
        departmentService.patch(department, new PatchDepartmentRequest(null, null, null, true));
        service.redeem(citizen, 0, secret);
        assertThat(memberships.count()).isEqualTo(2);
        assertThat(departmentService.findById(department).code()).isEqualTo("MUNIC");
    }

    @Test
    void redemptionWaitsForDepartmentPatchAndRejectsCommittedDeactivation() throws Exception {
        var invitation = issue(RoleCode.TECHNICIAN);
        String secret = code();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> patch = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
                departmentService.patch(department, new PatchDepartmentRequest(null, null, null, false));
                locked.countDown();
                await(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<?> redemption = executor.submit(() -> fails(422, "INVALID_DEPARTMENT",
                        () -> service.redeem(citizen, 0, secret)));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting;
                do {
                    waiting = jdbc.queryForObject("""
                            select exists(select 1 from pg_stat_activity
                            where datname=current_database() and wait_event_type='Lock'
                            and query like '%departments%' and pid <> pg_backend_pid())
                            """, Boolean.class);
                    if (!waiting)
                        Thread.sleep(20);
                } while (!waiting && System.nanoTime() < deadline);
                assertThat(waiting).as("El canje espera el PATCH de departamento").isTrue();
                release.countDown();
                patch.get(10, TimeUnit.SECONDS);
                redemption.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertThat(memberships.count()).isZero();
        assertThat(audit.count()).isEqualTo(1);
        assertThat(service.get(admin, 0, invitation.id()).status()).isEqualTo(InvitationStatus.ACTIVE);
    }

    @Test
    void expirationWhileWaitingForDepartmentLockDoesNotGrantPermission() throws Exception {
        var invitation = issue(RoleCode.TECHNICIAN);
        String secret = code();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch locked = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
                jdbc.queryForObject("select id from departments where id=? for update", UUID.class, department);
                locked.countDown();
                await(release);
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<?> redemption = executor.submit(() -> fails(422, "INVALID_INVITATION_CODE",
                        () -> service.redeem(citizen, 0, secret)));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting;
                do {
                    waiting = jdbc.queryForObject("""
                            select exists(select 1 from pg_stat_activity
                            where datname=current_database() and wait_event_type='Lock'
                            and query like '%departments%' and pid <> pg_backend_pid())
                            """, Boolean.class);
                    if (!waiting)
                        Thread.sleep(20);
                } while (!waiting && System.nanoTime() < deadline);
                assertThat(waiting).as("El canje está esperando el bloqueo del departamento").isTrue();
                now = invitation.expiresAt();
                release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                redemption.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertThat(memberships.count()).isZero();
        assertThat(audit.count()).isEqualTo(1);
        assertThat(service.get(admin, 0, invitation.id()).status()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void wrongIdentityUsedRevokedExpiredAndUnknownCodesHaveSameNonEnumerableError() {
        var dto = issue(RoleCode.OPERATOR);
        String secret = code();
        fails(422, "INVALID_INVITATION_CODE", () -> service.redeem(admin, 0, secret));
        fails(422, "INVALID_INVITATION_CODE", () -> service.redeem(citizen, 0, codes.generate()));
        now = dto.expiresAt();
        fails(422, "INVALID_INVITATION_CODE", () -> service.redeem(citizen, 0, secret));
        fails(409, "INVITATION_NOT_ACTIVE", () -> service.revoke(admin, 0, dto.id()));
        now = dto.createdAt();
        service.revoke(admin, 0, dto.id());
        fails(422, "INVALID_INVITATION_CODE", () -> service.redeem(citizen, 0, secret));
        var second = issue(RoleCode.OPERATOR);
        String secondCode = code();
        service.redeem(citizen, 0, secondCode);
        fails(422, "INVALID_INVITATION_CODE", () -> service.redeem(citizen, 0, secondCode));
        fails(409, "INVITATION_NOT_ACTIVE", () -> service.revoke(admin, 0, second.id()));
    }

    @Test
    void revocationRecordsActorAndCannotBeRepeated() {
        var dto = issue(RoleCode.ADMIN);
        service.revoke(admin, 0, dto.id());
        var revoked = service.get(admin, 0, dto.id());
        assertThat(revoked.revokedBy()).isEqualTo(admin);
        assertThat(revoked.revokedAt()).isEqualTo(now);
        assertThat(revoked.redeemedBy()).isNull();
        fails(409, "INVITATION_NOT_ACTIVE", () -> service.revoke(admin, 0, dto.id()));
        assertThat(audit.count()).isEqualTo(2);
        fails(404, "INVITATION_NOT_FOUND", () -> service.get(admin, 0, UUID.randomUUID()));
    }

    @Test
    void revalidatesActiveVerifiedCitizenAdminAndJwtVersionInsideTransactions() {
        issue(RoleCode.ADMIN);
        String secret = code();
        fails(403, "FORBIDDEN",
                () -> service.list(citizen, 0, new InvitationQuery(null, null, null, null, null, null)));
        jdbc.update("update users set email_verified=false where id=?", citizen);
        fails(403, "EMAIL_NOT_VERIFIED", () -> service.redeem(citizen, 0, secret));
        jdbc.update("update users set email_verified=true, token_version=1 where id=?", citizen);
        fails(401, "UNAUTHORIZED", () -> service.redeem(citizen, 0, secret));
        jdbc.update("update users set token_version=0, active=false where id=?", citizen);
        fails(401, "UNAUTHORIZED", () -> service.redeem(citizen, 0, secret));
        jdbc.update("update users set active=true where id=?", citizen);
        jdbc.update("delete from user_roles where user_id=?", citizen);
        fails(401, "UNAUTHORIZED", () -> service.redeem(citizen, 0, secret));
        assertThat(invitations.findAll().getFirst().getRedeemedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = { "OPERATOR", "TECHNICIAN" })
    void duplicateInvitationsAreAllowedButExistingPermissionNeverConsumesAnother(RoleCode role) {
        issue(role);
        service.redeem(citizen, 0, code());
        var duplicate = issue(role);
        String secret = code();
        fails(409, "PERMISSION_ALREADY_GRANTED", () -> service.redeem(citizen, 0, secret));
        assertThat(invitations.count()).isEqualTo(2);
        assertThat(service.get(admin, 0, duplicate.id()).status()).isEqualTo(InvitationStatus.ACTIVE);
        assertThat(audit.count()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = { "OPERATOR", "TECHNICIAN" })
    void concurrentSameCodeHasOneWinnerAndDifferentCodesDoNotConsumeLoser(RoleCode role) throws Exception {
        issue(role);
        String first = code();
        assertThat(race(first, first)).containsExactlyInAnyOrder(200, 422);
        // Un nuevo destinatario permite repetir la carrera con dos códigos distintos.
        citizen = fixture("second@example.org", true, false);
        issue("second@example.org", role);
        first = code();
        issue("second@example.org", role);
        String second = code();
        assertThat(race(first, second)).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select count(*) from staff_invitations where email='second@example.org' "
                + "and redeemed_at is null", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='STAFF_INVITATION_REDEEMED'",
                Integer.class)).isEqualTo(2);
    }

    private List<Integer> race(String first, String second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(code -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("Timeout");
                try {
                    service.redeem(citizen, 0, code);
                    return 200;
                } catch (ApiException ex) {
                    return ex.status().value();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedAuditRollsBackGrantConsumptionAndCreationAndNoEmailEscapesRollback() {
        var dto = issue(RoleCode.TECHNICIAN);
        String secret = code();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            issue(RoleCode.OPERATOR);
            assertThat(messages).hasSize(1);
            status.setRollbackOnly();
        });
        assertThat(messages).hasSize(1);
        assertThat(invitations.count()).isEqualTo(1);
        doThrow(new IllegalStateException("synthetic audit failure")).when(audit).saveAndFlush(any());
        assertThatThrownBy(() -> service.redeem(citizen, 0, secret)).isInstanceOf(IllegalStateException.class);
        assertThat(memberships.count()).isZero();
        assertThat(service.get(admin, 0, dto.id()).status()).isEqualTo(InvitationStatus.ACTIVE);
        assertThatThrownBy(() -> issue(RoleCode.ADMIN)).isInstanceOf(IllegalStateException.class);
        assertThat(invitations.count()).isEqualTo(1);
        assertThat(messages).hasSize(1);
        assertThatThrownBy(() -> service.revoke(admin, 0, dto.id())).isInstanceOf(IllegalStateException.class);
        assertThat(service.get(admin, 0, dto.id()).status()).isEqualTo(InvitationStatus.ACTIVE);
    }

    @Test
    void smtpFailureAfterCommitKeepsInvitationAndRedactsFailure(CapturedOutput output) {
        doThrow(new MailSendException("PRIVATE_SMTP person@example.org PRIVATE_SECRET")).when(sender).send(any());
        var dto = issue(RoleCode.ADMIN);
        assertThat(service.get(admin, 0, dto.id()).status()).isEqualTo(InvitationStatus.ACTIVE);
        assertThat(audit.count()).isEqualTo(1);
        assertThat(output.getAll()).contains("STAFF_INVITATION_EMAIL_FAILED")
                .doesNotContain("PRIVATE_SMTP", "PRIVATE_SECRET", "person@example.org");
    }

    @Test
    void redemptionWaitingForPasswordChangeRevalidatesVersionAfterLock() throws Exception {
        var dto = issue(RoleCode.OPERATOR);
        String secret = code();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> password = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                var user = users.findForPasswordChange(citizen).orElseThrow();
                user.changePassword("y".repeat(60));
                users.flush();
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> redemption = executor.submit(() -> service.redeem(citizen, 0, secret));
            try {
                assertThatThrownBy(() -> redemption.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            password.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> redemption.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex.getCause()).status().value()).isEqualTo(401));
        }
        assertThat(service.get(admin, 0, dto.id()).status()).isEqualTo(InvitationStatus.ACTIVE);
        assertThat(audit.count()).isEqualTo(1);
    }

    @Test
    void redemptionAndRevocationRaceLeavesExactlyOneAuditedFinalState() throws Exception {
        var dto = issue(RoleCode.OPERATOR);
        String secret = code();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> redemption = executor.submit(() -> {
                await(start);
                try {
                    service.redeem(citizen, 0, secret);
                    return 200;
                } catch (ApiException ex) {
                    return ex.status().value();
                }
            });
            Future<Integer> revocation = executor.submit(() -> {
                await(start);
                try {
                    service.revoke(admin, 0, dto.id());
                    return 204;
                } catch (ApiException ex) {
                    return ex.status().value();
                }
            });
            start.countDown();
            var results = List.of(redemption.get(10, TimeUnit.SECONDS), revocation.get(10, TimeUnit.SECONDS));
            assertThat(results).isIn(List.of(200, 409), List.of(422, 204));
        }
        var saved = service.get(admin, 0, dto.id());
        assertThat(saved.status()).isIn(InvitationStatus.REDEEMED, InvitationStatus.REVOKED);
        assertThat(audit.count()).isEqualTo(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Timeout del test");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test interrumpido");
        }
    }

    @Test
    void filtersBeforePagingWithStableTieBreakAndCorrectEmptyPageTotals() {
        var a = issue(RoleCode.OPERATOR);
        var b = issue(RoleCode.ADMIN);
        var c = issue(RoleCode.TECHNICIAN);
        service.revoke(admin, 0, b.id());
        var query = new InvitationQuery(InvitationStatus.ACTIVE, " PERSON@EXAMPLE.ORG ", null, null, 1, 1);
        var page = service.list(admin, 0, query);
        assertThat(page.pagination().totalItems()).isEqualTo(2);
        assertThat(page.pagination().totalPages()).isEqualTo(2);
        UUID newest = a.id().compareTo(c.id()) > 0 ? a.id() : c.id();
        assertThat(page.data().getFirst().id()).isEqualTo(newest);
        var empty = service.list(admin, 0, new InvitationQuery(InvitationStatus.ACTIVE, null, null, null, 3, 1));
        assertThat(empty.data()).isEmpty();
        assertThat(empty.pagination().totalItems()).isEqualTo(2);
        var filtered = service.list(admin, 0,
                new InvitationQuery(null, null, RoleCode.TECHNICIAN, department, 1, 20));
        assertThat(filtered.data()).hasSize(1);
        assertThat(filtered.data().getFirst().id()).isEqualTo(c.id());
        now = a.expiresAt();
        assertThat(service.list(admin, 0, new InvitationQuery(InvitationStatus.EXPIRED, null, null, null, 1, 20))
                .pagination().totalItems()).isEqualTo(2);
        fails(422, "VALIDATION_ERROR", () -> service.list(admin, 0,
                new InvitationQuery(null, null, null, null, Integer.MAX_VALUE, 100)));
    }

    @Test
    void cleanupRemovesUnverifiedAccountWithoutLosingInvitationThenNewAccountRedeems() {
        var dto = issue("new@example.org", RoleCode.OPERATOR);
        String secret = code();
        var old = auth.register(new RegisterRequest("Pendiente", "new@example.org", "password-123"));
        jdbc.update("update users set created_at=? where id=?",
                Timestamp.from(now.minus(Duration.ofHours(25))), old.user().id());
        assertThat(cleanup.clean().users()).isEqualTo(1);
        assertThat(invitations.findById(dto.id())).isPresent();
        var replacement = auth.register(new RegisterRequest("Verificada", "new@example.org", "password-123"));
        var match = Pattern.compile("[0-9]{8}").matcher(messages.getLast().text());
        assertThat(match.find()).isTrue();
        verification.confirm(replacement.user().id(), 0, match.group());
        service.redeem(replacement.user().id(), 0, secret);
        assertThat(service.get(admin, 0, dto.id()).redeemedBy()).isEqualTo(replacement.user().id());
        assertThat(replacement.user().id()).isNotEqualTo(old.user().id());
        assertThat(cleanup.clean().users()).isZero();
    }

    @Test
    void sqlEnforcesHashScopeStateDatesForeignKeysAndActiveMembershipHistory() {
        var dto = issue(RoleCode.OPERATOR);
        for (String assignment : List.of("code_hash=decode('00','hex')", "role_code='CITIZEN'",
                "role_code='TECHNICIAN'", "department_id='" + department + "'",
                "expires_at=created_at", "redeemed_at=created_at", "revoked_at=created_at",
                "created_by_user_id='" + UUID.randomUUID() + "'",
                "email=' Upper@Example.org '", "redeemed_at=expires_at, redeemed_by_user_id='" + citizen + "'")) {
            assertThatThrownBy(
                    () -> jdbc.update("update staff_invitations set " + assignment + " where id=?", dto.id()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        var other = issue(RoleCode.OPERATOR);
        assertThatThrownBy(() -> jdbc.update("update staff_invitations set code_hash="
                + "(select code_hash from staff_invitations where id=?) where id=?", dto.id(), other.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        service.revoke(admin, 0, dto.id());
        assertThatThrownBy(() -> jdbc.update("update staff_invitations set redeemed_at=created_at, "
                + "redeemed_by_user_id=? where id=?", citizen, dto.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        issue(RoleCode.TECHNICIAN);
        service.redeem(citizen, 0, code());
        assertThatThrownBy(() -> jdbc.update("insert into department_memberships "
                + "select uuidv7(),user_id,department_id,role_code,joined_at,left_at from department_memberships"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update department_memberships set role_code='ADMIN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update department_memberships set left_at=joined_at-interval '1 second'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update department_memberships set left_at=joined_at");
        issue(RoleCode.TECHNICIAN);
        service.redeem(citizen, 0, code());
        assertThat(memberships.count()).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("delete from users where id=?", admin))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class)).containsExactly("1");
    }

    @Test
    void httpRedemptionMakesPreviouslyIssuedJwtAdministrativeOnNextRequest() throws Exception {
        var token = auth.register(new RegisterRequest("Nueva admin", "promoted@example.org", "password-123"));
        var match = Pattern.compile("[0-9]{8}").matcher(messages.getLast().text());
        assertThat(match.find()).isTrue();
        verification.confirm(token.user().id(), 0, match.group());
        String bearer = "Bearer " + token.accessToken();
        mvc.perform(get("/api/v1/staff-invitations").header("Authorization", bearer)).andExpect(status().isForbidden());
        issue("promoted@example.org", RoleCode.ADMIN);
        String secret = code();
        mvc.perform(post("/api/v1/users/me/staff-invitation-redemptions").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"invitationCode\":\"" + secret + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/v1/staff-invitations").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.totalItems").value(1));
    }
}
