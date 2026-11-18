package es.grupo18.urbanpulse.maps.integration;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.maps.dto.MapFeatureDTO;
import es.grupo18.urbanpulse.maps.repository.MapIncidentRepository;
import es.grupo18.urbanpulse.maps.service.MapQuery;
import es.grupo18.urbanpulse.maps.service.MapQueryService;
import es.grupo18.urbanpulse.maps.service.MapVisibilityPolicy.Access;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.mail.EmailSender;
import es.grupo18.urbanpulse.support.PostgisDePruebas;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * Comprueba la visibilidad, los límites y la reutilización de celdas en PostgreSQL desechable.
 */
@SpringBootTest
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class MapPerformancePostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = PostgisDePruebas.nuevo();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MapIncidentRepository repository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MapQueryService maps;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    JsonMapper json;
    @MockitoBean
    EmailSender emailSender;
    private UUID reader;
    private UUID owner;
    private UUID operator;
    private UUID category;
    private static final MapQuery PROVINCE = new MapQuery(36, -6, 38, -3, 8, null, null);
    private static final MapQuery CITY = new MapQuery(36.70, -4.46, 36.76, -4.38, 13, null, null);
    private static final MapQuery CLOSE = new MapQuery(36.719, -4.424, 36.724, -4.419, 18, null, null);

    @BeforeEach
    void fixtures() {
        jdbc.execute(
                "truncate duplicate_links, incident_information_responses, attachments, status_changes, incident_reviews, priority_decisions, incidents, audit_events");
        reader = account(false);
        owner = account(false);
        operator = account(true);
        category = UUID.randomUUID();
        jdbc.update("insert into categories(id,name) values(?,?)", category, "Synthetic " + category);
    }

    @Test
    void aThousandCoastalIncidentsReuseOnePublicPointWithoutChangingVisibilityOrLimits() throws Exception {
        var coast = jdbc.queryForMap("""
                WITH vertices AS (SELECT unnest(map_vertices_v1()) p), coastal AS MATERIALIZED (
                    SELECT p FROM vertices
                    WHERE map_lat_v1(p) BETWEEN 36.65 AND 36.75 AND map_lon_v1(p) BETWEEN -4.6 AND -4.4
                        AND NOT (point((floor(p[0]/100)+0.5)*100,(floor(p[1]/100)+0.5)*100) <@ map_outline_v1())
                    LIMIT 1
                ), points AS MATERIALIZED (
                    SELECT p, map_public_point_v1(map_lat_v1(p),map_lon_v1(p)) q FROM coastal
                )
                SELECT map_lat_v1(p) lat, map_lon_v1(p) lon, map_lat_v1(q) public_lat, map_lon_v1(q) public_lon,
                    map_lat_v1(p+d) alt_lat, map_lon_v1(p+d) alt_lon
                FROM points, (VALUES (point(1,0)),(point(-1,0)),(point(0,1)),(point(0,-1))) offsets(d)
                WHERE p+d <@ map_outline_v1()
                    AND floor((p+d)[0]/100)=floor(p[0]/100) AND floor((p+d)[1]/100)=floor(p[1]/100)
                LIMIT 1
                """);
        double lat = number(coast, "lat"), lon = number(coast, "lon");
        var publicPoint = new MapFeatureDTO.Coordinates(number(coast, "public_lat"), number(coast, "public_lon"));
        jdbc.update(
                """
                        INSERT INTO incidents(id,reporter_user_id,title,description,latitude,longitude,status,category_id,created_at,updated_at)
                        SELECT gen_random_uuid(),?,'PRIVATE TITLE','PRIVATE DESCRIPTION',
                            CASE WHEN g%2=0 THEN ? ELSE ? END, CASE WHEN g%2=0 THEN ? ELSE ? END,
                            CASE WHEN g%3=0 THEN 'REPORTED' ELSE 'VALIDATED' END,
                            CASE WHEN g%2=0 THEN ?::uuid ELSE NULL END,now(),now()
                        FROM generate_series(1,1000) g
                        """,
                owner, lat, number(coast, "alt_lat"), lon, number(coast, "alt_lon"), category);
        jdbc.update("""
                INSERT INTO incident_reviews(id,incident_id,reviewed_by_user_id,decision,reviewed_at,resulting_status)
                SELECT gen_random_uuid(),id,?,'VALIDATE',now(),'VALIDATED' FROM incidents
                """, operator);
        analyze();

        var result = assertTimeout(Duration.ofSeconds(5), () -> maps.find(reader, 0, PROVINCE));
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.totalIncidents()).isEqualTo(1000);
        assertThat(result.items().getFirst().coordinates()).isEqualTo(publicPoint);
        assertThat(publicCalls(PROVINCE)).isEqualTo(1);
        assertServiceProjection(PROVINCE);

        var publicBox = around(publicPoint.latitude(), publicPoint.longitude());
        var originalBox = around(lat, lon);
        var individual = maps.find(reader, 0, publicBox);
        assertThat(individual.count()).isEqualTo(1000);
        assertThat(individual.items()).allSatisfy(item -> {
            var marker = (MapFeatureDTO.IncidentFeature) item;
            assertThat(marker.coordinates()).isEqualTo(publicPoint);
            assertThat(marker.canAccessPrivateDetail()).isFalse();
            assertThat(marker.coordinatePrecision()).isEqualTo("APPROXIMATE");
        });
        assertThat(json.writeValueAsString(individual)).doesNotContain("PRIVATE", "description", "reporter");
        assertThat(maps.find(reader, 0, originalBox).totalIncidents()).isZero();
        var filtered = new MapQuery(36, -6, 38, -3, 8, IncidentStatus.VALIDATED, category);
        assertThat(maps.find(reader, 0, filtered).totalIncidents()).isEqualTo(334);
        assertServiceProjection(filtered);

        // No VALIDATE history: even a VALIDATED status must not be counted publicly.
        for (int n = 0; n < 17; n++)
            incident(owner, lat, lon, "VALIDATED", false);
        UUID own = incident(reader, lat, lon, "REJECTED", false);
        assertThat(maps.find(reader, 0, publicBox).totalIncidents()).isEqualTo(1000);
        var ownMarker = (MapFeatureDTO.IncidentFeature) maps.find(reader, 0, originalBox).items().getFirst();
        assertThat(ownMarker.incidentId()).isEqualTo(own);
        assertThat(ownMarker.coordinates()).isEqualTo(new MapFeatureDTO.Coordinates(lat, lon));
        assertThat(ownMarker.canAccessPrivateDetail()).isTrue();
        assertThat(maps.find(operator, 0, PROVINCE).totalIncidents()).isEqualTo(1018);
        assertThat(maps.find(operator, 0, originalBox).items()).allSatisfy(item -> {
            var marker = (MapFeatureDTO.IncidentFeature) item;
            assertThat(marker.coordinatePrecision()).isEqualTo("EXACT");
            assertThat(marker.coordinates()).isEqualTo(new MapFeatureDTO.Coordinates(lat, lon));
        });
        incident(owner, lat, lon, "VALIDATED", true);
        assertThatThrownBy(() -> maps.find(reader, 0, publicBox)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo("MAP_RESULT_LIMIT"));
        assertThat(maps.find(reader, 0, PROVINCE).totalIncidents()).isEqualTo(1002);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1000, 10000, 50000 })
    void mixedLoadPreservesVisibleCountsAcrossFiltersAndZoom(int size) throws Exception {
        jdbc.execute("drop table if exists diagnostic_seed");
        // A normal test-only staging table avoids dependence on pooled connection
        // affinity.
        jdbc.execute(
                "create table diagnostic_seed as select g,gen_random_uuid() id from generate_series(1," + size + ") g");
        jdbc.update(
                """
                        INSERT INTO incidents(id,reporter_user_id,title,description,latitude,longitude,status,category_id,created_at,updated_at)
                        SELECT id,CASE WHEN g%5=0 THEN ?::uuid ELSE ?::uuid END,'PRIVATE TITLE','PRIVATE DESCRIPTION',
                            CASE WHEN g%100<45 THEN 36.72+((g*37)%1000)*0.00003 WHEN g%100<60 THEN 36.7213
                                WHEN g%100<80 THEN 36.52+((g*37)%1000)*0.00002
                                WHEN g%100<90 THEN 36.74+((g*37)%1000)*0.00001 ELSE 37.18 END,
                            CASE WHEN g%100<45 THEN -4.44+((g*61)%1000)*0.00004 WHEN g%100<60 THEN -4.4214
                                WHEN g%100<80 THEN -4.90+((g*61)%1000)*0.00002
                                WHEN g%100<90 THEN -5.17+((g*61)%1000)*0.00001 ELSE -3.60 END,
                            CASE g%4 WHEN 0 THEN 'REPORTED' WHEN 1 THEN 'VALIDATED' WHEN 2 THEN 'REJECTED' ELSE 'CLOSED' END,
                            CASE WHEN g%10=1 THEN ?::uuid ELSE NULL END,now(),now() FROM diagnostic_seed
                        """,
                reader, owner, category);
        jdbc.update("""
                INSERT INTO incident_reviews(id,incident_id,reviewed_by_user_id,decision,reviewed_at,resulting_status)
                SELECT gen_random_uuid(),id,?,'VALIDATE',now(),'VALIDATED' FROM diagnostic_seed WHERE g%3<>0
                """, operator);
        analyze();
        assertThat(maps.find(reader, 0, PROVINCE).totalIncidents()).isEqualTo(size * 66L / 100);
        assertThat(maps.find(operator, 0, PROVINCE).totalIncidents()).isEqualTo(size * 90L / 100);
        assertServiceProjection(PROVINCE);
        assertServiceProjection(CITY);
        assertServiceProjection(new MapQuery(36, -6, 38, -3, 8, IncidentStatus.VALIDATED, category));
        assertServiceProjection(CLOSE);
        // Every distinct authorized cell is evaluated once, not once per incident.
        Long cells = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT DISTINCT floor((map_xy_v1(i.latitude,i.longitude))[0]/100),
                        floor((map_xy_v1(i.latitude,i.longitude))[1]/100)
                    FROM incidents i WHERE reporter_user_id<>? AND map_in_malaga_v1(latitude,longitude)
                        AND EXISTS(SELECT 1 FROM incident_reviews r WHERE r.incident_id=i.id AND r.decision='VALIDATE')
                ) cells
                """, Long.class, reader);
        assertThat(publicCalls(PROVINCE)).isEqualTo(cells);
    }

    /**
     * Per-transaction counters give a deterministic regression assertion, independent of CPU speed.
     */
    private long publicCalls(MapQuery query) {
        var tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setReadOnly(true);
        return tx.execute(status -> {
            jdbc.execute("SET LOCAL track_functions='pl'");
            maps.find(reader, 0, query);
            return jdbc.queryForObject("SELECT coalesce(sum(calls),0) FROM pg_stat_xact_user_functions "
                    + "WHERE schemaname='public' AND funcname='map_public_point_v1'", Long.class);
        });
    }

    private void assertServiceProjection(MapQuery input) {
        var rows = repository.find(input, new Access(reader, false));
        if (rows.size() > MapIncidentRepository.MAX_FEATURES) {
            assertThatThrownBy(() -> maps.find(reader, 0, input)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.code()).isEqualTo("MAP_RESULT_LIMIT"));
        } else {
            var dto = assertTimeout(Duration.ofSeconds(5), () -> maps.find(reader, 0, input));
            assertThat(dto.count()).isEqualTo(rows.size());
            assertThat(dto.totalIncidents()).isEqualTo(rows.stream().mapToLong(row -> row.incidentCount()).sum());
        }
    }

    private UUID account(boolean global) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,display_name,email,password_hash,email_verified,created_at,updated_at) "
                + "VALUES(?,'Synthetic user',?,?,true,now(),now())", id, id + "@example.org", "x".repeat(60));
        jdbc.update("INSERT INTO user_roles VALUES(?,'CITIZEN')", id);
        if (global)
            jdbc.update("INSERT INTO user_roles VALUES(?,'OPERATOR')", id);
        return id;
    }

    private UUID incident(UUID reporter, double lat, double lon, String state, boolean validated) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO incidents(id,reporter_user_id,title,description,latitude,longitude,status,created_at,updated_at)
                        VALUES(?,?,'PRIVATE TITLE','PRIVATE DESCRIPTION',?,?,?,now(),now())
                        """,
                id, reporter, lat, lon, state);
        if (validated)
            jdbc.update(
                    "INSERT INTO incident_reviews(id,incident_id,reviewed_by_user_id,decision,reviewed_at,resulting_status) "
                            + "VALUES(?,?,?,'VALIDATE',now(),'VALIDATED')",
                    UUID.randomUUID(), id, operator);
        return id;
    }

    private void analyze() {
        jdbc.execute("ANALYZE incidents");
        jdbc.execute("ANALYZE incident_reviews");
    }

    private static double number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).doubleValue();
    }

    private static MapQuery around(double lat, double lon) {
        return new MapQuery(lat - 0.0000001, lon - 0.0000001, lat + 0.0000001, lon + 0.0000001, 18, null, null);
    }
}
