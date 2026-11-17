package es.grupo18.urbanpulse.processing;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Instalación consolidada sin Spring: integridad y conservación de datos
 * válidos.
 */
@Testcontainers
class ProcessingMigrationPostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName
                    .parse("postgis/postgis:18-3.6-alpine")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void installsFinalV1AndPreservesReviewsPhotosAndResponseInvariants() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("1").load().migrate();
        UUID user = UUID.randomUUID(), incident = UUID.randomUUID(), legacy = UUID.randomUUID();
        jdbc.update(
                "insert into users(id,display_name,email,password_hash,created_at,updated_at) values(?,'Usuario','migration@example.org',repeat('x',60),now(),now())",
                user);
        jdbc.update(
                "insert into incidents(id,reporter_user_id,title,description,latitude,longitude,status,created_at,updated_at,version) values(?,?,'Aviso válido','Descripción suficiente',0,0,'REPORTED',now(),now(),0)",
                incident, user);
        jdbc.update(
                "insert into incident_reviews(id,incident_id,reviewed_by_user_id,decision,reason,reviewed_at,resulting_status) values(?,?,?,'VALIDATE',null,now(),'VALIDATED')",
                legacy, incident, user);
        jdbc.update(
                "insert into attachments(id,incident_id,uploaded_by,original_filename,content_type,storage_key,size_bytes,created_at) values(uuidv7(),?,?,'photo.jpg','IMAGE_JPEG','opaque',42,now())",
                incident, user);
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class)).containsExactly("2");
        assertThat(jdbc.queryForObject("select reason from incident_reviews where id=?", String.class, legacy))
                .isNull();
        assertThat(
                jdbc.queryForObject("select resulting_status from incident_reviews where id=?", String.class, legacy))
                .isEqualTo("VALIDATED");
        assertThat(jdbc.queryForObject("select count(*) from attachments", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "insert into incident_reviews(id,incident_id,reviewed_by_user_id,decision,reviewed_at) values(uuidv7(),?,?,'VALIDATE',now())",
                incident, user))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID request = UUID.randomUUID();
        jdbc.update(
                "insert into incident_reviews(id,incident_id,reviewed_by_user_id,decision,reason,reviewed_at,resulting_status) values(?,?,?,'REQUEST_INFORMATION','Datos',now(),'AWAITING_INFORMATION')",
                request, incident, user);
        jdbc.update("update incidents set status='AWAITING_INFORMATION' where id=?", incident);
        jdbc.update(
                "insert into status_changes(id,incident_id,from_status,to_status,changed_at) values(uuidv7(),?,'REPORTED','AWAITING_INFORMATION',now())",
                incident);
        String response = "insert into incident_information_responses(id,incident_id,review_id,responded_by_user_id,message,responded_at) values(uuidv7(),?,?,?,?,now())";
        assertThatThrownBy(() -> jdbc.update(response, incident, legacy, user, "Mensaje"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(response, UUID.randomUUID(), request, user, "Mensaje"))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID other = UUID.randomUUID();
        jdbc.update(
                "insert into incidents(id,reporter_user_id,title,description,latitude,longitude,status,created_at,updated_at,version) values(?,?,'Otro aviso','Descripción suficiente',0,0,'REPORTED',now(),now(),0)",
                other, user);
        assertThatThrownBy(() -> jdbc.update(response, other, request, user, "Mensaje"))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String message : new String[] { null, "ab", " ", " texto", "texto\t", "\u2003".repeat(3),
                "🚦".repeat(2001) }) {
            assertThatThrownBy(() -> jdbc.update(response, incident, request, user, message))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(jdbc.update(response, incident, request, user, "🚦".repeat(2000))).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(response, incident, request, user, "Segunda respuesta"))
                .isInstanceOf(DataIntegrityViolationException.class);
        Flyway.configure().dataSource(source).load().validate();
    }

    /**
     * Sustituye el ensayo histórico V1/V5/V2; V1 actual no actualiza bases
     * anteriores.
     */
    @Test
    void reinstallIsIdempotentAndAllConstraintsAreValidated() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var flyway = Flyway.configure().dataSource(source).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE' and table_name <> 'flyway_schema_history'",
                Integer.class)).isEqualTo(17);
        assertThat(jdbc.queryForObject(
                "select count(*) from pg_constraint where connamespace='public'::regnamespace and not convalidated",
                Integer.class)).isZero();
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success", String.class))
                .containsExactly("1", "2");
        assertThat(jdbc.queryForList(
                "select proname from pg_proc where pronamespace='public'::regnamespace and proname like 'map_%'",
                String.class))
                .containsExactlyInAnyOrder("map_xy_v1", "map_lat_v1", "map_lon_v1", "map_outline_v1", "map_vertices_v1",
                        "map_in_malaga_v1", "map_public_point_v1");
        assertThat(jdbc.queryForList(
                "select indexname from pg_indexes where schemaname='public' and indexname like 'maps_%'", String.class))
                .containsExactlyInAnyOrder("maps_exact_location_idx", "maps_public_location_idx",
                        "maps_validated_review_idx");
        assertThat(jdbc.queryForList("select code from roles", String.class))
                .containsExactlyInAnyOrder("CITIZEN", "OPERATOR", "ADMIN", "DEPARTMENT_COORDINATOR", "TECHNICIAN");
        for (String table : java.util.List.of("users", "departments", "categories", "incidents", "duplicate_links"))
            assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class)).isZero();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
    }
}
