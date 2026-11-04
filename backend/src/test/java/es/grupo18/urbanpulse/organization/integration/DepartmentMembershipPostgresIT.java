package es.grupo18.urbanpulse.organization.integration;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.identity.repository.RoleRepository;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.organization.model.Department;
import es.grupo18.urbanpulse.organization.model.DepartmentMembership;
import es.grupo18.urbanpulse.organization.repository.DepartmentMembershipRepository;
import es.grupo18.urbanpulse.organization.repository.DepartmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración de las pertenencias departamentales sobre PostgreSQL.
 * Verifica el mapeo JPA, la recuperación de departamento y rol, la conservación
 * del historial y las restricciones de unicidad y ámbito aplicadas por Flyway.
 */
@SpringBootTest
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class DepartmentMembershipPostgresIT {

    /**
     * Base de datos PostgreSQL efímera utilizada durante las pruebas.
     */
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    /**
     * Configura las propiedades de conexión para utilizar el contenedor de pruebas.
     *
     * @param registry registro dinámico de propiedades de Spring
     */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DepartmentMembershipRepository memberships;

    @Autowired
    UserRepository users;

    @Autowired
    DepartmentRepository departments;

    @Autowired
    RoleRepository roles;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    es.grupo18.urbanpulse.organization.service.DepartmentMembershipService service;

    /**
     * Elimina las pertenencias y usuarios creados por cada prueba.
     * Crea su propio departamento y conserva los cinco roles del catálogo.
     */
    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from department_memberships");
        jdbc.update("delete from email_verifications");
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        jdbc.update("delete from departments");
        jdbc.update(
                "insert into departments(id,name,code) values ('0199f6de-0001-7000-8000-000000000001','Unidad de prueba','MANT')");
    }

    /**
     * Inserta un usuario mínimo directamente en PostgreSQL para aislar
     * las pruebas de pertenencias del flujo de registro y autenticación.
     *
     * @param email correo electrónico único del usuario de prueba
     * @return identificador del usuario insertado
     */
    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.update("""
                insert into users (
                    id, display_name, email, password_hash,
                    active, email_verified, token_version,
                    created_at, updated_at
                )
                values (?, ?, ?, ?, true, true, 0, ?, ?)
                """,
                id,
                "Usuario de prueba",
                email,
                "x".repeat(60),
                Timestamp.from(now),
                Timestamp.from(now));

        return id;
    }

    /**
     * Inserta una pertenencia mediante SQL para comprobar directamente
     * las restricciones de integridad definidas en la migración.
     *
     * @param userId       identificador del usuario
     * @param departmentId identificador del departamento
     * @param roleCode     código del rol departamental
     * @param joinedAt     fecha de alta
     * @param leftAt       fecha de baja, o null si permanece activa
     */
    private void insertMembership(
            UUID userId,
            UUID departmentId,
            RoleCode roleCode,
            Instant joinedAt,
            Instant leftAt) {

        jdbc.update("""
                insert into department_memberships (
                    id, user_id, department_id, role_code, joined_at, left_at
                )
                values (uuidv7(), ?, ?, ?, ?, ?)
                """,
                userId,
                departmentId,
                roleCode.name(),
                Timestamp.from(joinedAt),
                leftAt == null ? null : Timestamp.from(leftAt));
    }

    /**
     * Comprueba que JPA persiste una pertenencia y recupera sus relaciones
     * con el departamento y el rol sin consultas posteriores por cada campo.
     */
    @Test
    @Transactional
    void savesAndLoadsMembershipWithDepartmentAndRole() {
        UUID userId = insertUser("membership@example.org");
        User user = users.findById(userId).orElseThrow();
        Department department = departments.findById(
                UUID.fromString("0199f6de-0001-7000-8000-000000000001")).orElseThrow();
        Role role = roles.findById(RoleCode.TECHNICIAN).orElseThrow();
        Instant joinedAt = Instant.parse("2026-01-10T09:00:00Z");

        memberships.saveAndFlush(new DepartmentMembership(user, department, role, joinedAt));

        var result = memberships.findAllForUsers(List.of(userId));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getUser().getId()).isEqualTo(userId);
        assertThat(result.getFirst().getDepartment().getCode()).isEqualTo("MANT");
        assertThat(result.getFirst().getRole().getCode()).isEqualTo(RoleCode.TECHNICIAN);
        assertThat(result.getFirst().getJoinedAt()).isEqualTo(joinedAt);
        assertThat(result.getFirst().getLeftAt()).isNull();
        assertThat(result.getFirst().isActive()).isTrue();
    }

    /**
     * Comprueba que la consulta devuelve tanto las pertenencias finalizadas
     * como la adscripción activa más reciente del mismo usuario.
     */
    @Test
    void returnsActiveAndHistoricalMembershipsInChronologicalOrder() {
        UUID userId = insertUser("history@example.org");
        UUID departmentId = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        Instant firstJoined = Instant.parse("2025-01-01T09:00:00Z");
        Instant firstLeft = Instant.parse("2025-06-01T09:00:00Z");
        Instant secondJoined = Instant.parse("2026-01-01T09:00:00Z");

        insertMembership(userId, departmentId, RoleCode.TECHNICIAN, firstJoined, firstLeft);
        insertMembership(userId, departmentId, RoleCode.TECHNICIAN, secondJoined, null);

        var result = memberships.findAllForUsers(List.of(userId));

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getJoinedAt()).isEqualTo(secondJoined);
        assertThat(result.getFirst().isActive()).isTrue();
        assertThat(result.get(1).getJoinedAt()).isEqualTo(firstJoined);
        assertThat(result.get(1).getLeftAt()).isEqualTo(firstLeft);
        assertThat(result.get(1).isActive()).isFalse();
    }

    /**
     * Comprueba que PostgreSQL impide dos pertenencias activas con la misma
     * combinación de usuario, departamento y rol.
     */
    @Test
    void rejectsDuplicateActiveMembershipForSameUserDepartmentAndRole() {
        UUID userId = insertUser("duplicate@example.org");
        UUID departmentId = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        Instant joinedAt = Instant.parse("2026-01-01T09:00:00Z");

        insertMembership(userId, departmentId, RoleCode.TECHNICIAN, joinedAt, null);

        assertThatThrownBy(() -> insertMembership(
                userId, departmentId, RoleCode.TECHNICIAN, joinedAt.plusSeconds(60), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Comprueba que una nueva pertenencia activa puede crearse después
     * de finalizar la anterior, conservando ambos registros históricos.
     */
    @Test
    void allowsNewActiveMembershipAfterPreviousMembershipHasEnded() {
        UUID userId = insertUser("returning@example.org");
        UUID departmentId = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        Instant firstJoined = Instant.parse("2025-01-01T09:00:00Z");
        Instant firstLeft = Instant.parse("2025-06-01T09:00:00Z");
        Instant secondJoined = Instant.parse("2026-01-01T09:00:00Z");

        insertMembership(userId, departmentId, RoleCode.TECHNICIAN, firstJoined, firstLeft);
        insertMembership(userId, departmentId, RoleCode.TECHNICIAN, secondJoined, null);

        assertThat(jdbc.queryForObject(
                "select count(*) from department_memberships where user_id = ?",
                Integer.class,
                userId)).isEqualTo(2);
    }

    /**
     * Comprueba que la base de datos rechaza roles globales en una pertenencia
     * departamental, manteniendo la separación definida en ADR-0009.
     */
    @Test
    void rejectsGlobalRoleInDepartmentMembership() {
        UUID userId = insertUser("global-role@example.org");
        UUID departmentId = UUID.fromString("0199f6de-0001-7000-8000-000000000001");

        assertThatThrownBy(() -> insertMembership(
                userId,
                departmentId,
                RoleCode.ADMIN,
                Instant.parse("2026-01-01T09:00:00Z"),
                null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Comprueba que la base de datos rechaza fechas de baja anteriores
     * a la fecha de alta.
     */
    @Test
    void rejectsLeftAtBeforeJoinedAt() {
        UUID userId = insertUser("invalid-dates@example.org");
        UUID departmentId = UUID.fromString("0199f6de-0001-7000-8000-000000000001");

        assertThatThrownBy(() -> insertMembership(
                userId,
                departmentId,
                RoleCode.TECHNICIAN,
                Instant.parse("2026-06-01T09:00:00Z"),
                Instant.parse("2026-05-01T09:00:00Z")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Comprueba que una misma persona puede desempeñar roles departamentales
     * distintos dentro del mismo departamento.
     */
    @Test
    void allowsDifferentDepartmentalRolesForSameUserAndDepartment() {
        UUID userId = insertUser("multiple-roles@example.org");
        UUID departmentId = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        Instant joinedAt = Instant.parse("2026-01-01T09:00:00Z");

        insertMembership(userId, departmentId, RoleCode.TECHNICIAN, joinedAt, null);
        insertMembership(userId, departmentId, RoleCode.DEPARTMENT_COORDINATOR, joinedAt, null);

        assertThat(jdbc.queryForObject(
                "select count(*) from department_memberships where user_id = ? and left_at is null",
                Integer.class,
                userId)).isEqualTo(2);
    }

    @Test
    void servicePreservesHistoryAndRequiresVerifiedActiveAccount() {
        UUID user = insertUser("service-history@example.org");
        UUID department = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        jdbc.update("update users set email_verified=false where id=?", user);
        assertThatThrownBy(() -> service.assign(user, department, RoleCode.TECHNICIAN))
                .isInstanceOf(es.grupo18.urbanpulse.shared.error.ApiException.class)
                .extracting("code").isEqualTo("USER_EMAIL_NOT_VERIFIED");
        jdbc.update("update users set email_verified=true,active=false where id=?", user);
        assertThatThrownBy(() -> service.assign(user, department, RoleCode.TECHNICIAN))
                .extracting("code").isEqualTo("USER_INACTIVE");
        jdbc.update("update users set active=true where id=?", user);
        var first = service.assign(user, department, RoleCode.TECHNICIAN);
        assertThatThrownBy(() -> service.assign(user, department, RoleCode.TECHNICIAN))
                .extracting("code").isEqualTo("DEPARTMENT_MEMBERSHIP_DUPLICATE");
        Instant end = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        service.end(first.getId(), end);
        assertThatThrownBy(() -> service.end(first.getId(), end.plusSeconds(5)))
                .extracting("code").isEqualTo("DEPARTMENT_MEMBERSHIP_CANNOT_END");
        var second = service.assign(user, department, RoleCode.TECHNICIAN);
        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(memberships.findAllForUsers(List.of(user))).hasSize(2);
        assertThat(memberships.findById(first.getId()).orElseThrow().getLeftAt())
                .isEqualTo(end.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void concurrentAssignmentsAndEndingsHaveOneWinnerAndKeepHistory() throws Exception {
        UUID user = insertUser("concurrent-membership@example.org");
        UUID department = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(n -> pool.submit(() -> {
                start.await();
                try {
                    service.assign(user, department, RoleCode.TECHNICIAN);
                    return "OK";
                } catch (es.grupo18.urbanpulse.shared.error.ApiException ex) {
                    return ex.code();
                }
            })).toList();
            start.countDown();
            assertThat(List.of(tasks.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
                    tasks.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "DEPARTMENT_MEMBERSHIP_DUPLICATE");
            UUID membership = jdbc.queryForObject("select id from department_memberships where user_id=?", UUID.class,
                    user);
            var endStart = new java.util.concurrent.CountDownLatch(1);
            Instant firstEnd = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var endings = java.util.stream.IntStream.range(0, 2).mapToObj(n -> pool.submit(() -> {
                endStart.await();
                try {
                    service.end(membership, firstEnd.plusSeconds(n));
                    return "OK" + n;
                } catch (es.grupo18.urbanpulse.shared.error.ApiException ex) {
                    return ex.code();
                }
            })).toList();
            endStart.countDown();
            var results = List.of(endings.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
                    endings.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results).contains("DEPARTMENT_MEMBERSHIP_CANNOT_END");
            int winner = results.contains("OK0") ? 0 : 1;
            assertThat(memberships.findById(membership).orElseThrow().getLeftAt())
                    .isEqualTo(firstEnd.plusSeconds(winner));
            assertThat(memberships.count()).isEqualTo(1);
        }
    }
}
