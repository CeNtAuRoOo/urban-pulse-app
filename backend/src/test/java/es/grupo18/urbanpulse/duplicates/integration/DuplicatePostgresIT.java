package es.grupo18.urbanpulse.duplicates.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class DuplicatePostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayV2PersistsCanonicalPairsAndRejectsRepeatedOrSelfLinks() {
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class)).containsExactly("1", "2");
        UUID user = UUID.fromString("00000000-0000-7000-8000-000000000003");
        UUID first = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID second = UUID.fromString("00000000-0000-7000-8000-000000000002");
        Instant now = Instant.parse("2026-11-14T12:00:00Z");
        jdbc.update("insert into users(id,display_name,email,password_hash,created_at,updated_at) "
                + "values (?,?,?,?,?,?)", user, "Operador", "duplicate-it@example.test", "x".repeat(60), Timestamp.from(now), Timestamp.from(now));
        insertIncident(first, user, now);
        insertIncident(second, user, now);

        insertLink(UUID.randomUUID(), first, second, user, now);
        assertThat(jdbc.queryForObject("select count(*) from duplicate_links", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> insertLink(UUID.randomUUID(), first, second, user, now))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertLink(UUID.randomUUID(), first, first, user, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertIncident(UUID id, UUID reporter, Instant now) {
        jdbc.update("insert into incidents(id,reporter_user_id,title,description,latitude,longitude,status,created_at,updated_at) "
                + "values (?,?,?,?,?,?,?,?,?)", id, reporter, "Aviso de prueba", "Descripción de prueba válida.",
                36.7, -4.4, "REPORTED", Timestamp.from(now), Timestamp.from(now));
    }

    private void insertLink(UUID id, UUID first, UUID second, UUID actor, Instant now) {
        jdbc.update("insert into duplicate_links(id,incident_id,candidate_incident_id,linked_by_user_id,linked_at) "
                + "values (?,?,?,?,?)", id, first, second, actor, Timestamp.from(now));
    }
}
