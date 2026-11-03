
package es.grupo18.urbanpulse;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.User;
import es.grupo18.urbanpulse.repository.UserRepository;

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

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from email_verifications");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
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
                .extracting(UserRepository.SecurityState::getRoleCode)
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
}
