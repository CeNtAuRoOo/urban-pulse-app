package es.grupo18.urbanpulse.processing;

import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.io.TempDir;
import es.grupo18.urbanpulse.maps.repository.MapIncidentRepository;
import es.grupo18.urbanpulse.maps.service.MapQuery;
import es.grupo18.urbanpulse.maps.service.MapVisibilityPolicy.Access;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Migración real sin Spring: conserva una V1 poblada y exige el contrato nuevo
 * al insertar.
 */
@Testcontainers
class ProcessingMigrationPostgresIT {
    @Container
    final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Test
    void migratesPopulatedV1WithoutInventingHistoryOrLosingPhotos() {
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
                "insert into incident_reviews(id,incident_id,reviewed_by_user_id,decision,reason,reviewed_at) values(?,?,?,'VALIDATE','Motivo histórico',now())",
                legacy, incident, user);
        jdbc.update(
                "insert into attachments(id,incident_id,uploaded_by,original_filename,content_type,storage_key,size_bytes,created_at) values(uuidv7(),?,?,'photo.jpg','IMAGE_JPEG','opaque',42,now())",
                incident, user);
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class)).containsExactly("1", "2", "5");
        assertThat(jdbc.queryForObject("select reason from incident_reviews where id=?", String.class, legacy))
                .isEqualTo("Motivo histórico");
        assertThat(
                jdbc.queryForObject("select resulting_status from incident_reviews where id=?", String.class, legacy))
                .isNull();
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

    @Test
    void lateV2IsRejectedByDefaultAndOneOffMigrationPreservesMapsAndEvidence(@TempDir Path oldMigrations)
            throws Exception {
        // Reproduce the previous branch exactly: the same immutable V1 and V5, no V2 yet.
        for (String name : new String[] { "V1__initial_schema.sql", "V5__maps_geography.sql" }) {
            try (var sql = new ClassPathResource("db/migration/" + name).getInputStream()) {
                Files.copy(sql, oldMigrations.resolve(name));
            }
        }
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        assertThat(Flyway.configure().dataSource(source).locations("filesystem:" + oldMigrations)
                .load().migrate().migrationsExecuted).isEqualTo(2);
        UUID author = UUID.randomUUID(), operator = UUID.randomUUID(), reader = UUID.randomUUID();
        for (UUID id : new UUID[] { author, operator, reader }) {
            jdbc.update("insert into users(id,display_name,email,password_hash,email_verified,created_at,updated_at) "
                    + "values(?,'Migration user',?,repeat('x',60),true,now(),now())", id, id + "@example.org");
            jdbc.update("insert into user_roles values(?,'CITIZEN')", id);
        }
        jdbc.update("insert into user_roles values(?,'OPERATOR')", operator);
        UUID incident = UUID.randomUUID(), review = UUID.randomUUID(), attachment = UUID.randomUUID();
        jdbc.update("insert into incidents(id,reporter_user_id,title,description,latitude,longitude,status,created_at,updated_at) "
                + "values(?,?,'Private title','Private description',36.7213,-4.4214,'VALIDATED',now(),now())", incident, author);
        jdbc.update("insert into incident_reviews(id,incident_id,reviewed_by_user_id,decision,reason,reviewed_at) "
                + "values(?,?,?,'VALIDATE','Legacy reason',now())", review, incident, operator);
        jdbc.update("insert into attachments(id,incident_id,uploaded_by,original_filename,content_type,storage_key,size_bytes,created_at) "
                + "values(?,?,?,'photo.jpg','IMAGE_JPEG','opaque-migration-key',42,now())", attachment, incident, author);
        var checksums = jdbc.queryForList("select version,checksum from flyway_schema_history where success order by installed_rank");
        var repository = new MapIncidentRepository(source);
        var viewport = new MapQuery(36, -6, 38, -3, 8, null, null);
        var before = repository.find(viewport, new Access(reader, false));
        assertThat(before).hasSize(1);
        assertThat(before.getFirst().exact()).isFalse();

        var defaults = Flyway.configure().dataSource(source).load();
        assertThat(defaults.getConfiguration().isOutOfOrder()).isFalse();
        assertThat(defaults.validateWithResult().validationSuccessful).isFalse();
        assertThatThrownBy(defaults::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class)).containsExactly("1", "5");
        assertThat(jdbc.queryForObject("select to_regclass('incident_information_responses')::text", String.class)).isNull();

        // Test-only rehearsal of a reviewed, one-off operator procedure. No application setting is changed.
        assertThat(Flyway.configure().dataSource(source).outOfOrder(true).load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class)).containsExactly("1", "5", "2");
        assertThat(jdbc.queryForList("select version,checksum from flyway_schema_history "
                + "where success and version in ('1','5') order by installed_rank")).isEqualTo(checksums);
        assertThat(repository.find(viewport, new Access(reader, false))).isEqualTo(before);
        assertThat(jdbc.queryForObject("select resulting_status from incident_reviews where id=?", String.class, review)).isNull();
        assertThat(jdbc.queryForObject("select reason from incident_reviews where id=?", String.class, review)).isEqualTo("Legacy reason");
        assertThat(jdbc.queryForObject("select storage_key from attachments where id=?", String.class, attachment))
                .isEqualTo("opaque-migration-key");
        assertThat(jdbc.queryForObject("select count(*) from incident_information_responses", Long.class)).isZero();
        assertThat(defaults.validateWithResult().validationSuccessful).isTrue();
        assertThat(defaults.migrate().migrationsExecuted).isZero();
        assertThat(defaults.getConfiguration().isOutOfOrder()).isFalse();
    }
}
