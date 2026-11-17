package es.grupo18.urbanpulse.duplicates.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import es.grupo18.urbanpulse.auth.service.EmailVerificationCleanup;
import es.grupo18.urbanpulse.duplicates.service.DuplicateScorer;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class DuplicatePostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName
                    .parse("postgis/postgis:18-3.6-alpine")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JwtEncoder encoder;
    @Autowired
    JsonMapper json;
    @Autowired
    DuplicateScorer scorer;
    @Autowired
    EmailVerificationCleanup cleanup;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @BeforeEach
    void isolate() {
        jdbc.execute(
                "truncate duplicate_links, incident_information_responses, attachments, status_changes, incident_reviews, priority_decisions, incidents, audit_events");
    }

    @Test
    void flywayV1PersistsCanonicalPairsAndRejectsRepeatedOrSelfLinks() {
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class)).containsExactly("1", "2");
        UUID user = UUID.fromString("00000000-0000-7000-8000-000000000003");
        UUID first = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID second = UUID.fromString("00000000-0000-7000-8000-000000000002");
        Instant now = Instant.parse("2026-11-14T12:00:00Z");
        jdbc.update("insert into users(id,display_name,email,password_hash,created_at,updated_at) "
                + "values (?,?,?,?,?,?)", user, "Operador", "duplicate-it@example.test", "x".repeat(60),
                Timestamp.from(now), Timestamp.from(now));
        insertIncident(first, user, now);
        insertIncident(second, user, now);

        insertLink(UUID.randomUUID(), first, second, user, now);
        assertThat(jdbc.queryForObject("select count(*) from duplicate_links", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> insertLink(UUID.randomUUID(), first, second, user, now))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertLink(UUID.randomUUID(), first, first, user, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void httpRequiresCurrentGlobalRoleAndVerifiedAccount() throws Exception {
        UUID author = account(null), source = incident(author, NOW, 36.7, -4.4, null);
        UUID other = incident(author, NOW, 36.7, -4.4, null);
        for (String suffix : List.of("duplicate-candidates", "duplicate-links")) {
            boolean post = suffix.endsWith("links");
            String body = post ? json.writeValueAsString(Map.of("candidateIncidentId", other)) : null;
            assertThat(http(null, source, suffix, body).statusCode()).isEqualTo(401);
            assertThat(http(author, source, suffix, body).statusCode()).isEqualTo(403);
            UUID local = account(null);
            UUID department = UUID.randomUUID();
            jdbc.update("insert into departments(id,name,code) values(?,?,?)", department, department.toString(),
                    "D" + department.toString().substring(0, 15).replace("-", "").toUpperCase(java.util.Locale.ROOT));
            jdbc.update(
                    "insert into department_memberships(id,user_id,department_id,role_code,joined_at) values(uuidv7(),?,?,'TECHNICIAN',now())",
                    local, department);
            assertThat(http(local, source, suffix, body).statusCode()).isEqualTo(403);
            UUID operator = account("OPERATOR");
            jdbc.update("update users set email_verified=false where id=?", operator);
            assertThat(http(operator, source, suffix, body).statusCode()).isEqualTo(403);
            jdbc.update("update users set email_verified=true,active=false where id=?", operator);
            assertThat(http(operator, source, suffix, body).statusCode()).isEqualTo(401);
        }
        for (String role : List.of("ADMIN", "OPERATOR")) {
            UUID actor = account(role);
            assertThat(http(actor, source, "duplicate-candidates", null).statusCode()).isEqualTo(200);
            assertThat(http(actor, UUID.randomUUID(), "duplicate-candidates", null).statusCode()).isEqualTo(404);
            jdbc.update("delete from user_roles where user_id=? and role_code=?", actor, role);
            assertThat(http(actor, source, "duplicate-candidates", null).statusCode()).isEqualTo(403);
        }
    }

    @Test
    void httpCanonicalPairUnicodeValidationAndOriginalReportsRemainIntact() throws Exception {
        UUID actor = account("ADMIN");
        UUID low = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-7fff-bfff-ffffffffffff");
        insertIncident(low, actor, NOW);
        insertIncident(high, actor, NOW);
        assertThat(link(actor, low, low, null).statusCode()).isEqualTo(422);
        assertThat(link(actor, low, UUID.randomUUID(), null).statusCode()).isEqualTo(404);
        assertThat(http(actor, low, "duplicate-links", "{}").statusCode()).isEqualTo(422);
        assertThat(http(actor, low, "duplicate-links", "{broken").statusCode()).isEqualTo(400);
        for (String reason : List.of("x".repeat(2001), "🚦".repeat(2001), "\u0000")) {
            assertThat(link(actor, low, high, reason).statusCode())
                    .as("Motivo inválido de %s puntos", reason.codePointCount(0, reason.length())).isEqualTo(422);
        }
        assertThat(
                http(actor, low, "duplicate-links", "{\"candidateIncidentId\":\"" + high + "\",\"reason\":\"\\ud800\"}")
                        .statusCode())
                .isEqualTo(422);
        var response = link(actor, high, low, "  " + "🚦".repeat(2000) + "  ");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(json.readTree(response.body()).get("incidentId").asString()).isEqualTo(high.toString());
        assertThat(jdbc.queryForObject("select incident_id from duplicate_links", UUID.class)).isEqualTo(low);
        assertThat(jdbc.queryForObject("select char_length(reason) from duplicate_links", Integer.class))
                .isEqualTo(2000);
        assertThat(link(actor, low, high, null).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='DUPLICATE_LINK_CONFIRMED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select status from incidents", String.class)).containsOnly("REPORTED");
        assertThat(jdbc.queryForList("select version from incidents", Long.class)).containsOnly(0L);
        assertThat(json.readTree(http(actor, low, "duplicate-candidates", null).body()).size()).isZero();
        assertThat(json.readTree(http(actor, high, "duplicate-candidates", null).body()).size()).isZero();
    }

    @Test
    void rankingConsidersAllValidCandidatesAndMatchesJavaOracle() throws Exception {
        UUID actor = account("OPERATOR"), sourceCategory = category("Origen");
        UUID source = incident(actor, NOW, 36.7, -4.4, sourceCategory);
        for (int n = 0; n < 250; n++)
            incident(actor, NOW.plusSeconds(n), 40, -4.4, null);
        var expected = new java.util.ArrayList<Ranked>();
        for (int n = 0; n < 130; n++) {
            double latitude = 36.7 + (130 - n) * 0.00001;
            Instant time = NOW.minusSeconds(n * 60);
            UUID cat = n % 3 == 0 ? null : n % 3 == 1 ? sourceCategory : category("Distinta" + n);
            UUID id = incident(actor, time, latitude, -4.4, cat);
            String name = cat == null ? null : cat.equals(sourceCategory) ? "Origen" : "Distinta" + n;
            expected.add(new Ranked(id, scorer.score(36.7, -4.4, NOW, "Origen", latitude, -4.4, time, name).score()));
        }
        expected.sort(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(r -> r.id().toString()));
        var result = json.readTree(http(actor, source, "duplicate-candidates", null).body());
        assertThat(result.size()).isEqualTo(20);
        for (int n = 0; n < 20; n++) {
            assertThat(result.get(n).get("candidateIncidentId").asString()).isEqualTo(expected.get(n).id().toString());
            assertThat(result.get(n).get("score").asDouble()).isCloseTo(expected.get(n).score(),
                    org.assertj.core.data.Offset.offset(1e-12));
        }
        assertThat(jdbc.queryForObject("select count(*) from duplicate_links", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_events", Integer.class)).isZero();
    }

    @Test
    void tiedScoresUsePostgresUuidOrderWithAndWithoutCategory() throws Exception {
        UUID actor = account("ADMIN"), category = category("Empates");
        UUID source = incident(actor, NOW, 0, 0, category);
        var ids = new java.util.ArrayList<UUID>();
        for (int n = 0; n < 25; n++)
            ids.add(incident(actor, NOW, 0, 0, category));
        ids.sort(Comparator.comparing(UUID::toString));
        for (boolean missing : List.of(false, true)) {
            if (missing)
                jdbc.update("update incidents set category_id=null where id=?", source);
            var response = http(actor, source, "duplicate-candidates", null);
            assertThat(response.statusCode()).isEqualTo(200);
            var result = json.readTree(response.body());
            assertThat(result.size()).isEqualTo(20);
            for (int n = 0; n < 20; n++)
                assertThat(result.get(n).get("candidateIncidentId").asString()).isEqualTo(ids.get(n).toString());
        }
    }

    @Test
    void temporalBoundariesDistanceAndAntimeridianRemainInclusive() throws Exception {
        UUID actor = account("OPERATOR"), source = incident(actor, NOW, 0, 179.9999, null);
        UUID before = incident(actor, NOW.minus(Duration.ofHours(72)), 0, -179.9999, null);
        UUID after = incident(actor, NOW.plus(Duration.ofHours(72)), 0, -179.9999, null);
        UUID tooEarly = incident(actor, NOW.minus(Duration.ofHours(72)).minusNanos(1000), 0, -179.9999, null);
        UUID tooLate = incident(actor, NOW.plus(Duration.ofHours(72)).plusNanos(1000), 0, -179.9999, null);
        UUID far = incident(actor, NOW, 0, 179.99, null);
        String body = http(actor, source, "duplicate-candidates", null).body();
        assertThat(json.readTree(body).size()).isEqualTo(2);
        assertThat(body).contains(before.toString(), after.toString()).doesNotContain(tooEarly.toString(),
                tooLate.toString(), far.toString(), source.toString());
        UUID zero = incident(actor, NOW, 0, 0, null);
        UUID edge = incident(actor, NOW, Math.toDegrees(250.0 / 6371000.0), 0, null);
        UUID outside = incident(actor, NOW, Math.toDegrees(250.01 / 6371000.0), 0, null);
        String boundary = http(actor, zero, "duplicate-candidates", null).body();
        assertThat(boundary).contains(edge.toString()).doesNotContain(outside.toString());
        jdbc.update("update incidents set created_at=?,updated_at=? where id=?",
                Timestamp.from(NOW.plus(Duration.ofHours(72))), Timestamp.from(NOW.plus(Duration.ofHours(72))), edge);
        assertThat(http(actor, zero, "duplicate-candidates", null).body()).contains(edge.toString());
    }

    @Test
    void concurrentReverseConfirmationsProduceOneLinkAndOneAudit() throws Exception {
        UUID actor = account("OPERATOR"), a = incident(actor, NOW, 0, 0, null), b = incident(actor, NOW, 0, 0, null);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                barrier.await();
                return link(actor, a, b, null).statusCode();
            });
            var second = executor.submit(() -> {
                barrier.await();
                return link(actor, b, a, null).statusCode();
            });
            assertThat(List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from duplicate_links", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_events where action='DUPLICATE_LINK_CONFIRMED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackTheConfirmedLink() throws Exception {
        UUID actor = account("OPERATOR"), a = incident(actor, NOW, 0, 0, null), b = incident(actor, NOW, 0, 0, null);
        jdbc.execute(
                "alter table audit_events add constraint test_duplicate_audit check(action <> 'DUPLICATE_LINK_CONFIRMED')");
        try {
            assertThat(link(actor, a, b, null).statusCode()).isEqualTo(500);
        } finally {
            jdbc.execute("alter table audit_events drop constraint test_duplicate_audit");
        }
        assertThat(jdbc.queryForObject("select count(*) from duplicate_links", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_events", Integer.class)).isZero();
    }

    @Test
    void actualSqlTimeoutReturns503WithoutPartialSuggestions() throws Exception {
        UUID actor = account("OPERATOR"), a = incident(actor, NOW, 0, 0, null);
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("lock table duplicate_links in access exclusive mode");
            var response = http(actor, a, "duplicate-candidates", null);
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("DUPLICATE_QUERY_TIMEOUT");
            connection.rollback();
        }
    }

    @Test
    void sqlForeignKeysAndCleanupPreserveConfirmingUserWithoutAudit() {
        UUID actor = account("OPERATOR"), author = account(null), a = incident(author, NOW, 0, 0, null),
                b = incident(author, NOW, 0, 0, null);
        UUID low = a.toString().compareTo(b.toString()) < 0 ? a : b, high = low.equals(a) ? b : a;
        insertLink(UUID.randomUUID(), low, high, actor, NOW);
        assertThatThrownBy(() -> jdbc.update("delete from incidents where id=?", a))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from users where id=?", actor))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertLink(UUID.randomUUID(), high, low, actor, NOW))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update users set email_verified=false,created_at=now()-interval '3 days' where id=?", actor);
        cleanup.clean();
        assertThat(jdbc.queryForObject("select count(*) from users where id=?", Integer.class, actor)).isEqualTo(1);
    }

    private record Ranked(UUID id, double score) {
    }

    private UUID category(String name) {
        return jdbc.queryForObject("insert into categories(name) values(?) returning id", UUID.class, name);
    }

    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into users(id,display_name,email,password_hash,email_verified,created_at,updated_at) values(?,'Usuario',?,repeat('x',60),true,now(),now())",
                id, id + "@example.test");
        jdbc.update("insert into user_roles values(?,'CITIZEN')", id);
        if (role != null)
            jdbc.update("insert into user_roles values(?,?)", id, role);
        return id;
    }

    private UUID incident(UUID author, Instant time, double lat, double lon, UUID category) {
        UUID id = UUID.randomUUID();
        insertIncident(id, author, time);
        jdbc.update("update incidents set latitude=?,longitude=?,category_id=? where id=?", lat, lon, category, id);
        return id;
    }

    private HttpResponse<String> link(UUID actor, UUID source, UUID other, String reason) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("candidateIncidentId", other);
        body.put("reason", reason);
        return http(actor, source, "duplicate-links", json.writeValueAsString(body));
    }

    private HttpResponse<String> http(UUID actor, UUID source, String suffix, String body) throws Exception {
        var request = HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/incidents/" + source + "/" + suffix))
                .timeout(Duration.ofSeconds(20));
        if (actor != null) {
            Instant now = Instant.now();
            var claims = JwtClaimsSet.builder().issuer("urbanpulse-tests").subject(actor.toString()).issuedAt(now)
                    .expiresAt(now.plusSeconds(3600)).claim("ver", 0).build();
            request.header("Authorization",
                    "Bearer " + encoder
                            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                            .getTokenValue());
        }
        if (body != null)
            request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void insertIncident(UUID id, UUID reporter, Instant now) {
        jdbc.update(
                "insert into incidents(id,reporter_user_id,title,description,latitude,longitude,status,created_at,updated_at) "
                        + "values (?,?,?,?,?,?,?,?,?)",
                id, reporter, "Aviso de prueba", "Descripción de prueba válida.",
                36.7, -4.4, "REPORTED", Timestamp.from(now), Timestamp.from(now));
    }

    private void insertLink(UUID id, UUID first, UUID second, UUID actor, Instant now) {
        jdbc.update("insert into duplicate_links(id,incident_id,candidate_incident_id,linked_by_user_id,linked_at) "
                + "values (?,?,?,?,?)", id, first, second, actor, Timestamp.from(now));
    }
}
