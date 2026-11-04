
package es.grupo18.urbanpulse.identity.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.identity.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class UserRepositoryPostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    es.grupo18.urbanpulse.identity.service.UserService service;
    @Autowired
    es.grupo18.urbanpulse.auth.service.EmailVerificationCleanup cleanup;
    @Autowired
    jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from audit_events");
        jdbc.update("delete from staff_invitations");
        jdbc.update("delete from department_memberships");
        jdbc.update("delete from email_verifications");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        jdbc.update("delete from departments");
        jdbc.update(
                "insert into departments(id,name,code) values ('0199f6de-0001-7000-8000-000000000001','Unidad de prueba','MANT')");
    }

    private UUID insertUser(
            String displayName,
            String email,
            boolean active,
            boolean emailVerified,
            long tokenVersion,
            Instant createdAt) {

        UUID id = UUID.randomUUID();

        jdbc.update("""
                insert into users (
                    id, display_name, email, password_hash,
                    active, email_verified, token_version,
                    created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                displayName,
                email,
                "x".repeat(60),
                active,
                emailVerified,
                tokenVersion,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));

        return id;
    }

    private void assignRole(UUID userId, RoleCode role) {
        jdbc.update(
                "insert into user_roles (user_id, role_code) values (?, ?)",
                userId,
                role.name());
    }

    /**
     * Inserta una pertenencia departamental para el usuario mediante SQL.
     *
     * @param userId identificador del usuario
     * @param role   código del rol departamental
     * @param leftAt fecha de baja, o null si la pertenencia continúa activa
     */
    private void assignDepartmentRole(UUID userId, RoleCode role, boolean active) {
        jdbc.update("""
                insert into department_memberships (
                    id, user_id, department_id, role_code, joined_at, left_at
                )
                values (
                    uuidv7(), ?, '0199f6de-0001-7000-8000-000000000001',
                    ?, CURRENT_TIMESTAMP - interval '2 hours',
                    case when ? then null else CURRENT_TIMESTAMP - interval '1 hour' end
                )
                """,
                userId,
                role.name(),
                active);
    }

    @Test
    void findByEmailReturnsUserAndLoadsRoles() {
        UUID id = insertUser(
                "Ciudadano",
                "citizen@example.org",
                true,
                true,
                0,
                Instant.now());

        assignRole(id, RoleCode.CITIZEN);

        User user = users.findByEmail("citizen@example.org").orElseThrow();

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getDisplayName()).isEqualTo("Ciudadano");
        assertThat(user.globalRoles()).containsExactly(RoleCode.CITIZEN);

        assertThat(users.findByEmail("missing@example.org")).isEmpty();
    }

    @Test
    void findForPasswordChangeReturnsUserInsideTransaction() {
        UUID id = insertUser(
                "Password",
                "password@example.org",
                true,
                true,
                0,
                Instant.now());

        User result = new TransactionTemplate(manager).execute(
                status -> users.findForPasswordChange(id).orElseThrow());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void findForEmailVerificationReturnsUserInsideTransaction() {
        UUID id = insertUser(
                "Verification",
                "verification@example.org",
                true,
                false,
                0,
                Instant.now());

        User result = new TransactionTemplate(manager).execute(
                status -> users.findForEmailVerification(id).orElseThrow());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.isEmailVerified()).isFalse();
    }

    @Test
    void findSecurityStateReturnsAccountStateAndRoles() {
        UUID id = insertUser(
                "Admin",
                "admin@example.org",
                true,
                true,
                3,
                Instant.now());

        assignRole(id, RoleCode.CITIZEN);
        assignRole(id, RoleCode.ADMIN);

        var states = users.findSecurityState(id);

        assertThat(states).hasSize(2);
        assertThat(states)
                .allSatisfy(state -> {
                    assertThat(state.getActive()).isTrue();
                    assertThat(state.getEmailVerified()).isTrue();
                    assertThat(state.getTokenVersion()).isEqualTo(3);
                });

        assertThat(states)
                .extracting(state -> state.getRoleCode())
                .containsExactlyInAnyOrder(RoleCode.CITIZEN, RoleCode.ADMIN);
    }

    @Test
    void findSecurityStateReturnsNullRoleWhenUserHasNoRoles() {
        UUID id = insertUser(
                "No roles",
                "noroles@example.org",
                false,
                false,
                0,
                Instant.now());

        var states = users.findSecurityState(id);

        assertThat(states).hasSize(1);
        assertThat(states.getFirst().getActive()).isFalse();
        assertThat(states.getFirst().getEmailVerified()).isFalse();
        assertThat(states.getFirst().getRoleCode()).isNull();
    }

    @Test
    void findUserWithRolesByIdReturnsUserAndRoles() {
        UUID id = insertUser(
                "Operator",
                "operator@example.org",
                true,
                true,
                0,
                Instant.now());

        assignRole(id, RoleCode.CITIZEN);
        assignRole(id, RoleCode.OPERATOR);

        User user = users.findUserWithRolesById(id).orElseThrow();

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.globalRoles())
                .containsExactlyInAnyOrder(RoleCode.CITIZEN, RoleCode.OPERATOR);

        assertThat(users.findUserWithRolesById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void searchUsersFiltersOrdersAndPaginates() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");

        UUID first = insertUser(
                "First",
                "first@example.org",
                true,
                true,
                0,
                base);

        UUID second = insertUser(
                "Second",
                "second@example.org",
                true,
                true,
                0,
                base.plusSeconds(60));

        UUID inactive = insertUser(
                "Inactive",
                "inactive@example.org",
                false,
                true,
                0,
                base.plusSeconds(120));

        assignRole(first, RoleCode.CITIZEN);
        assignRole(second, RoleCode.CITIZEN);
        assignRole(inactive, RoleCode.CITIZEN);

        Page<User> page = users.searchUsers(
                RoleCode.CITIZEN,
                true,
                PageRequest.of(
                        0,
                        1,
                        Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.asc("id"))));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getId()).isEqualTo(second);

        Page<User> secondPage = users.searchUsers(
                RoleCode.CITIZEN,
                true,
                PageRequest.of(
                        1,
                        1,
                        Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.asc("id"))));

        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent().getFirst().getId()).isEqualTo(first);
    }

    @Test
    void searchUsersWithoutFiltersReturnsAllUsers() {
        Instant base = Instant.parse("2026-02-01T10:00:00Z");

        insertUser(
                "Citizen",
                "citizen2@example.org",
                true,
                true,
                0,
                base);

        insertUser(
                "Inactive",
                "inactive2@example.org",
                false,
                false,
                0,
                base.plusSeconds(60));

        Page<User> page = users.searchUsers(
                null,
                null,
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    /**
     * Comprueba que el filtro por rol departamental devuelve únicamente
     * usuarios con una pertenencia activa de ese rol.
     */
    @Test
    void searchUsersFiltersByActiveDepartmentalRole() {
        Instant now = Instant.now();

        UUID technician = insertUser(
                "Technician",
                "technician@example.org",
                true,
                true,
                0,
                now);

        UUID citizen = insertUser(
                "Citizen",
                "citizen-only@example.org",
                true,
                true,
                0,
                now.plusSeconds(1));

        assignDepartmentRole(technician, RoleCode.TECHNICIAN, true);
        assignDepartmentRole(citizen, RoleCode.TECHNICIAN, false);

        Page<User> page = users.searchUsers(
                RoleCode.TECHNICIAN,
                true,
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent())
                .extracting(user -> user.getId())
                .containsExactly(technician);
        assertThat(citizen).isNotEqualTo(technician);
    }

    @Test
    void pageLoadsGlobalRolesInConstantQueries() {
        for (int i = 0; i < 12; i++) {
            UUID id = insertUser("Persona " + i, "page" + i + "@example.org", true, true, 0, Instant.now());
            assignRole(id, RoleCode.CITIZEN);
            assignRole(id, RoleCode.OPERATOR);
        }
        var stats = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            new TransactionTemplate(manager).executeWithoutResult(
                    status -> users.searchUsers(null, null, PageRequest.of(0, 10))
                            .forEach(user -> user.globalRoles()));
            long lazyQueries = stats.getPrepareStatementCount();
            stats.clear();
            var page = service.getUsers(1, 10, null, null);
            long batchQueries = stats.getPrepareStatementCount();
            System.out.println("USER_PAGE_QUERY_COUNTS lazy=" + lazyQueries + " batch=" + batchQueries);
            assertThat(lazyQueries).isGreaterThan(batchQueries);
            assertThat(batchQueries).isEqualTo(4);
            assertThat(page.pagination().totalItems()).isEqualTo(12);
            assertThat(page.data()).hasSize(10).allSatisfy(
                    dto -> assertThat(dto.roles()).containsExactlyInAnyOrder(RoleCode.CITIZEN, RoleCode.OPERATOR));
            stats.clear();
            assertThat(service.getUsers(1, 2, null, null).data()).hasSize(2);
            assertThat(stats.getPrepareStatementCount()).isEqualTo(batchQueries);
            assertThat(service.getUsers(20, 10, null, null).data()).isEmpty();
        } finally {
            stats.setStatisticsEnabled(false);
        }
    }

    @Test
    void cleanupPreservesReferencedAccountsAndContinuesBatch() {
        UUID referenced = insertUser("Histórico", "historical@example.org", true, false, 0,
                Instant.now().minusSeconds(172800));
        assignDepartmentRole(referenced, RoleCode.TECHNICIAN, false);
        UUID disposable = insertUser("Caducada", "expired@example.org", true, false, 0,
                Instant.now().minusSeconds(90000));
        assignRole(disposable, RoleCode.CITIZEN);
        assertThat(cleanup.clean().users()).isEqualTo(1);
        assertThat(users.existsById(referenced)).isTrue();
        assertThat(users.existsById(disposable)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from department_memberships", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentAdministratorDeactivationKeepsOneAvailable() throws Exception {
        UUID first = insertUser("Admin uno", "admin1@example.org", true, true, 0, Instant.now());
        UUID second = insertUser("Admin dos", "admin2@example.org", true, true, 0, Instant.now());
        assignRole(first, RoleCode.ADMIN);
        assignRole(second, RoleCode.ADMIN);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = java.util.List.of(first, second).stream().map(id -> pool.submit(() -> {
                start.await();
                try {
                    service.updateUser(id, new es.grupo18.urbanpulse.identity.dto.AdminUpdateUserRequest(false));
                    return "OK";
                } catch (es.grupo18.urbanpulse.shared.error.ApiException ex) {
                    return ex.code();
                }
            })).toList();
            start.countDown();
            var results = new java.util.ArrayList<String>();
            for (var future : futures)
                results.add(future.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("OK", "LAST_ACTIVE_ADMIN");
        }
        assertThat(users.countAvailableAdministrators()).isEqualTo(1);
        UUID inactive = jdbc.queryForObject("select id from users where active = false", UUID.class);
        service.updateUser(inactive, new es.grupo18.urbanpulse.identity.dto.AdminUpdateUserRequest(true));
        assertThat(users.countAvailableAdministrators()).isEqualTo(2);
    }

    @Test
    void profileWaitsForPasswordChangeAndPreservesTokenVersion() throws Exception {
        UUID id = insertUser("Perfil", "profile-lock@example.org", true, true, 0, Instant.now());
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var password = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                var user = users.findForPasswordChange(id).orElseThrow();
                user.changePassword("z".repeat(60));
                locked.countDown();
                try {
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("Timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var profile = pool.submit(() -> service.updateMe(id,
                    new es.grupo18.urbanpulse.identity.dto.UpdateMyProfileRequest(" Perfil nuevo ")));
            try {
                org.assertj.core.api.Assertions
                        .assertThatThrownBy(() -> profile.get(250, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally {
                release.countDown();
            }
            password.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(profile.get(20, java.util.concurrent.TimeUnit.SECONDS).displayName()).isEqualTo("Perfil nuevo");
        }
        User persisted = users.findById(id).orElseThrow();
        assertThat(persisted.getTokenVersion()).isEqualTo(1);
        assertThat(persisted.getPasswordHash()).isEqualTo("z".repeat(60));
    }

    @Test
    void databaseRejectsUnnormalizedBlankControlAndOversizeNames() {
        for (String name : java.util.List.of(" A ", "  ", "\u00a0\u00a0", "A\tB", "😀".repeat(121))) {
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(
                            () -> insertUser(name, UUID.randomUUID() + "@example.org", true, true, 0, Instant.now()))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        UUID valid = insertUser("😀".repeat(120), "unicode@example.org", true, true, 0, Instant.now());
        assertThat(users.findById(valid).orElseThrow().getDisplayName().codePointCount(0, 240)).isEqualTo(120);
    }

    @Test
    void cleanupSkipsEveryHistoricalForeignKeyBeforeLimitingBatch() {
        Instant old = Instant.now().minusSeconds(172800);
        UUID creator = insertUser("Emisora", "creator@example.org", true, false, 0, old);
        UUID redeemer = insertUser("Canjeadora", "redeemer@example.org", true, false, 0, old);
        UUID revoker = insertUser("Revocadora", "revoker@example.org", true, false, 0, old);
        UUID actor = insertUser("Auditada", "audited@example.org", true, false, 0, old);
        jdbc.update("""
                insert into staff_invitations(id,code_hash,email,role_code,created_by_user_id,
                    redeemed_by_user_id,created_at,expires_at,redeemed_at)
                values (uuidv7(),decode(repeat('01',32),'hex'),'recipient@example.org','OPERATOR',
                    ?,?,now()-interval '1 hour',now()+interval '1 hour',now())
                """, creator, redeemer);
        jdbc.update("""
                insert into staff_invitations(id,code_hash,email,role_code,created_by_user_id,
                    revoked_by_user_id,created_at,expires_at,revoked_at)
                values (uuidv7(),decode(repeat('02',32),'hex'),'recipient2@example.org','OPERATOR',
                    ?,?,now()-interval '1 hour',now()+interval '1 hour',now())
                """, creator, revoker);
        jdbc.update("insert into audit_events(id,actor_user_id,action,resource_type,resource_id,occurred_at) "
                + "values (uuidv7(),?,'TEST','USER','synthetic',now())", actor);
        for (int n = 0; n < 3; n++)
            insertUser("Eliminable", "disposable" + n + "@example.org", true, false, 0,
                    old.plusSeconds(60));
        var smallBatch = new es.grupo18.urbanpulse.auth.service.EmailVerificationCleanup(jdbc, manager,
                new es.grupo18.urbanpulse.auth.config.EmailVerificationProperties("unused-test-secret", 1, 10),
                java.time.Clock.systemUTC());
        assertThat(smallBatch.clean().users()).isEqualTo(3);
        assertThat(users.findAll())
                .extracting(user -> user.getId())
                .containsExactlyInAnyOrder(creator, redeemer, revoker, actor);
        assertThat(jdbc.queryForObject("select count(*) from staff_invitations", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_events", Integer.class)).isEqualTo(1);
    }
}
