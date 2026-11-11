package es.grupo18.urbanpulse.maps.integration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import es.grupo18.urbanpulse.maps.dto.MapFeatureDTO;
import es.grupo18.urbanpulse.maps.service.MapQuery;
import es.grupo18.urbanpulse.maps.service.MapQueryService;
import es.grupo18.urbanpulse.shared.mail.EmailSender;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT, current DB permissions, Flyway and native PostgreSQL geometry. No shared database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class MapPostgresIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired MapQueryService maps;
    @Autowired JsonMapper json;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;
    @MockitoBean EmailSender emailSender;
    private static final String URL = "/api/v1/map/incidents";
    private static final MapQuery PROVINCE = new MapQuery(36, -6, 38, -3, 8, null, null);
    private static final MapQuery CLOSE = new MapQuery(36.719, -4.424, 36.724, -4.419, 18, null, null);

    @BeforeEach
    void cleanIncidents() {
        jdbc.execute("truncate status_changes, incident_reviews, priority_decisions, incidents, audit_events");
    }

    @Test
    void authenticatesVerifiesAndRechecksDisabledAndRevokedAccounts() throws Exception {
        mvc.perform(request(PROVINCE)).andExpect(status().isUnauthorized());
        UUID actor = account(null);
        String token = bearer(actor);
        jdbc.update("update users set email_verified=false where id=?", actor);
        mvc.perform(request(PROVINCE).header("Authorization", token)).andExpect(status().isForbidden());
        jdbc.update("update users set email_verified=true, active=false where id=?", actor);
        mvc.perform(request(PROVINCE).header("Authorization", token)).andExpect(status().isUnauthorized());
        jdbc.update("update users set active=true, token_version=1 where id=?", actor);
        mvc.perform(request(PROVINCE).header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void publicationRequiresActualValidateHistoryAndSurvivesLaterStatuses() {
        UUID author = account(null), reader = account(null), operator = account("OPERATOR");
        UUID own = incident(reader, 36.7213, -4.4214, "REPORTED", null);
        incident(author, 36.7213, -4.4214, "REPORTED", null);
        incident(author, 36.7213, -4.4214, "REJECTED", null);
        incident(author, 36.7213, -4.4214, "RESOLVED", null); // state alone is not evidence
        UUID published = incident(author, 36.7213, -4.4214, "CLOSED", null);
        validate(published, operator);
        validate(published, operator); // EXISTS must not double-count reviews
        var result = maps.find(reader, 0, CLOSE);
        assertThat(result.totalIncidents()).isEqualTo(2);
        assertThat(result.items()).extracting(i -> ((MapFeatureDTO.IncidentFeature) i).incidentId())
                .containsExactlyInAnyOrder(own, published);
        assertThat(maps.find(operator, 0, CLOSE).totalIncidents()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = { "OPERATOR", "ADMIN" })
    void globalStaffSeesEveryStateWithExactCoordinatesAndCurrentDatabaseRoles(String role) throws Exception {
        UUID author = account(null), staff = account(role);
        UUID id = incident(author, 36.7213, -4.4214, "REJECTED", null);
        String token = bearer(staff);
        mvc.perform(request(CLOSE).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].incidentId").value(id.toString()))
                .andExpect(jsonPath("$.items[0].coordinates.latitude").value(36.7213))
                .andExpect(jsonPath("$.items[0].coordinatePrecision").value("EXACT"))
                .andExpect(jsonPath("$.items[0].canAccessPrivateDetail").value(true));
        jdbc.update("delete from user_roles where user_id=? and role_code=?", staff, role);
        mvc.perform(request(CLOSE).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncidents").value(0));
        jdbc.update("insert into user_roles values (?,?)", staff, role);
        mvc.perform(request(CLOSE).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncidents").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = { "TECHNICIAN", "DEPARTMENT_COORDINATOR" })
    void realDepartmentMembershipNeverGrantsGlobalVisibility(String role) {
        UUID department = UUID.randomUUID(), staff = account(null), author = account(null), operator = account("OPERATOR");
        jdbc.update("insert into departments(id,name,code) values(?,?,?)", department, "Map " + department,
                "M" + department.toString().replace("-", "").substring(0, 15).toUpperCase(java.util.Locale.ROOT));
        jdbc.update("insert into department_memberships values(?,?,?,?,now(),null)",
                UUID.randomUUID(), staff, department, role);
        incident(author, 36.7213, -4.4214, "REPORTED", null);
        UUID published = incident(author, 36.7214, -4.4213, "VALIDATED", null);
        validate(published, operator);
        UUID own = incident(staff, 36.7213, -4.4214, "REJECTED", null);
        var items = maps.find(staff, 0, CLOSE).items();
        assertThat(items).hasSize(2);
        for (var item : items) {
            var marker = (MapFeatureDTO.IncidentFeature) item;
            assertThat(marker.canAccessPrivateDetail()).isEqualTo(marker.incidentId().equals(own));
        }
    }

    @Test
    void exactForOwnerApproximateForOthersStableAcrossZoomAndPrivateDetailRemainsForbidden() throws Exception {
        UUID author = account(null), reader = account(null), operator = account("OPERATOR");
        UUID id = incident(author, 36.7213, -4.4214, "VALIDATED", null);
        validate(id, operator);
        var exact = (MapFeatureDTO.IncidentFeature) maps.find(author, 0, CLOSE).items().getFirst();
        var approximate = (MapFeatureDTO.IncidentFeature) maps.find(reader, 0, CLOSE).items().getFirst();
        assertThat(exact.coordinates()).isEqualTo(new MapFeatureDTO.Coordinates(36.7213, -4.4214));
        assertThat(approximate.coordinates()).isNotEqualTo(exact.coordinates());
        assertThat(approximate.coordinatePrecision()).isEqualTo("APPROXIMATE");
        assertThat(maps.find(reader, 0, CLOSE).items()).containsExactly(approximate);
        assertThat(maps.find(reader, 0, PROVINCE).items()).containsExactly(approximate);
        mvc.perform(get("/api/v1/incidents/" + id).header("Authorization", bearer(reader)))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewportPresenceUsesPublicPointEvenWhenOriginalIsOutsideViewport() {
        UUID author = account(null), reader = account(null), operator = account("OPERATOR");
        UUID id = incident(author, 36.7213, -4.4214, "VALIDATED", null);
        validate(id, operator);
        var point = maps.find(reader, 0, CLOSE).items().getFirst().coordinates();
        var aroundPublic = around(point.latitude(), point.longitude());
        var aroundOriginal = around(36.7213, -4.4214);
        assertThat(maps.find(reader, 0, aroundPublic).totalIncidents()).isEqualTo(1);
        assertThat(maps.find(reader, 0, aroundOriginal).totalIncidents()).isZero();
        assertThat(maps.find(author, 0, aroundOriginal).totalIncidents()).isEqualTo(1);
        assertThat(maps.find(author, 0, aroundPublic).totalIncidents()).isZero();
        // A move inside the SAME public cell must not alter any public observation.
        jdbc.update("update incidents set latitude=latitude+0.000001 where id=?", id);
        assertThat(maps.find(reader, 0, aroundPublic).items().getFirst().coordinates()).isEqualTo(point);
    }

    @Test
    void provinceIncludesOtherMunicipalitiesButExcludesNeighboursAndSea() {
        UUID author = account(null);
        for (double[] p : new double[][] {{36.7213,-4.4214}, {36.51,-4.885}, {36.742,-5.165},
                {37.019,-4.561}, {36.427,-5.146}, {36.779,-4.103}}) {
            assertThat(inside(p[0],p[1])).as("inside %s,%s",p[0],p[1]).isTrue();
            incident(author,p[0],p[1],"REPORTED",null);
        }
        for (double[] p : new double[][] {{37.177,-3.598}, {36.678,-5.445}, {36.68,-4.42}, {37.389,-5.984}}) {
            assertThat(inside(p[0],p[1])).as("outside %s,%s",p[0],p[1]).isFalse();
            incident(author,p[0],p[1],"REPORTED",null);
        }
        assertThat(maps.find(author,0,PROVINCE).totalIncidents()).isEqualTo(6);
    }

    @Test
    void officialBoundaryIsInclusiveAndNearBorderPublicPointIsSafe() {
        var boundary = jdbc.queryForMap("""
                WITH v AS (SELECT map_vertices_v1() AS a), edge AS (
                  SELECT a[n] AS a, a[n+1] AS b FROM v, generate_series(1,12338) n
                  WHERE map_lat_v1(a[n]) > 37 AND (a[n] <-> a[n+1]) > 30 LIMIT 1
                ) SELECT map_lat_v1(@@ lseg(a,b)) AS lat, map_lon_v1(@@ lseg(a,b)) AS lon,
                         a[0] AS ax, a[1] AS ay, b[0] AS bx, b[1] AS by FROM edge
                """);
        double lat = ((Number) boundary.get("lat")).doubleValue(), lon = ((Number) boundary.get("lon")).doubleValue();
        assertThat(inside(lat,lon)).isTrue();
        // Opposite sides within roughly a metre of a real northern boundary segment.
        double dx = ((Number) boundary.get("bx")).doubleValue() - ((Number) boundary.get("ax")).doubleValue();
        double dy = ((Number) boundary.get("by")).doubleValue() - ((Number) boundary.get("ay")).doubleValue();
        double norm = Math.hypot(dx,dy);
        boolean left = inside(lat + dx/norm*0.00001, lon - dy/norm*0.0000125);
        boolean right = inside(lat - dx/norm*0.00001, lon + dy/norm*0.0000125);
        assertThat(left).isNotEqualTo(right);
        Boolean publicInside = jdbc.queryForObject("select map_public_point_v1(?,?) <@ map_outline_v1()",
                Boolean.class,lat,lon);
        assertThat(publicInside).isTrue();
        UUID author = account(null), reader = account(null), operator = account("OPERATOR");
        UUID id = incident(author,lat,lon,"VALIDATED",null);
        validate(id,operator);
        assertThat(maps.find(reader,0,PROVINCE).totalIncidents()).isEqualTo(1);
    }

    @Test
    void clusteringSplitsCountsOnlyAuthorizedRowsAndAppliesFiltersBeforeGrouping() {
        UUID author = account(null), reader = account(null), operator = account("OPERATOR"), category = UUID.randomUUID();
        jdbc.update("insert into categories(id,name) values(?,?)",category,"Map " + category);
        for (int n=0;n<3;n++) {
            UUID id = incident(author,36.7213 + n*0.0001,-4.4214,"VALIDATED",category);
            validate(id,operator);
        }
        UUID closed = incident(author,36.7213,-4.4214,"CLOSED",null);
        validate(closed,operator);
        incident(author,36.7213,-4.4214,"REJECTED",null);
        var low = maps.find(reader,0,PROVINCE);
        assertThat(low.count()).isEqualTo(1);
        assertThat(low.totalIncidents()).isEqualTo(4);
        assertThat(((MapFeatureDTO.ClusterFeature)low.items().getFirst()).incidentCount()).isEqualTo(4);
        assertThat(maps.find(reader,0,PROVINCE)).isEqualTo(low);
        assertThat(maps.find(reader,0,CLOSE).count()).isEqualTo(4);
        assertThat(maps.find(reader,0,CLOSE).items()).extracting(MapFeatureDTO::coordinates)
                .contains(low.items().getFirst().coordinates());
        int previousCount = 0;
        for (int zoom : new int[] {8,12,15,17,18}) {
            var step = maps.find(reader,0,new MapQuery(CLOSE.minLat(),CLOSE.minLon(),CLOSE.maxLat(),
                    CLOSE.maxLon(),zoom,null,null));
            assertThat(step.totalIncidents()).isEqualTo(4);
            assertThat(step.count()).isGreaterThanOrEqualTo(previousCount);
            previousCount = step.count();
        }
        var filtered = maps.find(reader,0,new MapQuery(36,-6,38,-3,8,
                es.grupo18.urbanpulse.incidents.model.IncidentStatus.VALIDATED,category));
        assertThat(filtered.totalIncidents()).isEqualTo(3);
        assertThat(maps.find(reader,0,new MapQuery(36,-6,38,-3,8,null,UUID.randomUUID())).totalIncidents()).isZero();
    }

    @Test
    void rejectsInvalidMissingDuplicatedAndUnagreedParameters() throws Exception {
        String token = bearer(account(null));
        mvc.perform(get(URL).header("Authorization",token)).andExpect(status().isBadRequest());
        for (String suffix : List.of("&userId=" + UUID.randomUUID(), "&zoom=9", "&priority=HIGH", "&status=",
                "&status=BOGUS", "&categoryId=wrong", "&categoryId=1-1-1-1-1")) {
            mvc.perform(get(URL+"?minLat=36&minLon=-6&maxLat=38&maxLon=-3&zoom=8"+suffix)
                    .header("Authorization",token)).andExpect(status().isBadRequest());
        }
        for (String min : List.of("NaN","Infinity","-Infinity","39","hello")) {
            mvc.perform(get(URL+"?minLat="+min+"&minLon=-6&maxLat=38&maxLon=-3&zoom=8")
                    .header("Authorization",token)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(URL+"?minLat=36&minLon=-6&maxLat=38&maxLon=-3&zoom=23")
                .header("Authorization",token)).andExpect(status().isBadRequest());
    }

    @Test
    void tooManyIndividualFeaturesFailsWithoutPartialTotalsButLowZoomCountsAll() throws Exception {
        UUID author = account(null);
        jdbc.update("""
                insert into incidents(id,reporter_user_id,title,description,latitude,longitude,status,created_at,updated_at)
                select gen_random_uuid(),?,'Título privado','Descripción privada',36.7213,-4.4214,'REPORTED',now(),now()
                from generate_series(1,1001)
                """,author);
        mvc.perform(request(CLOSE).header("Authorization",bearer(author))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MAP_RESULT_LIMIT")).andExpect(jsonPath("$.items").doesNotExist());
        assertThat(maps.find(author,0,PROVINCE).totalIncidents()).isEqualTo(1001);
    }

    @Test
    void jsonVariantsOmitPrivateFieldsAndCaptureRealHttpExamples() throws Exception {
        UUID author = account(null), reader = account(null), operator = account("OPERATOR");
        UUID id = incident(author,36.7213,-4.4214,"VALIDATED",null);
        validate(id,operator);
        var marker = mvc.perform(request(CLOSE).header("Authorization",bearer(reader)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.items[0].type").value("INCIDENT"))
                .andExpect(jsonPath("$.items[0].title").value("Incidencia " + id))
                .andExpect(jsonPath("$.items[0].category").doesNotExist())
                .andExpect(jsonPath("$.items[0].priority").doesNotExist())
                .andExpect(jsonPath("$.items[0].clusterId").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(marker).doesNotContain("Título privado", "Descripción privada", "reporter", "email", "accuracyMeters");
        UUID second = incident(author,36.7214,-4.4213,"VALIDATED",null);
        validate(second,operator);
        var cluster = mvc.perform(request(PROVINCE).header("Authorization",bearer(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.totalIncidents").value(2))
                .andExpect(jsonPath("$.items[0].type").value("CLUSTER"))
                .andExpect(jsonPath("$.items[0].incidentCount").value(2))
                .andExpect(jsonPath("$.items[0].incidentId").doesNotExist())
                .andExpect(jsonPath("$.items[0].coordinatePrecision").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Files.createDirectories(Path.of("target/maps-examples"));
        // Capture actual HTTP over the running server as well as asserting MockMvc serialization.
        // Restrict the first response by deleting the second synthetic fixture before recapturing.
        var clusterHttp = http(reader, PROVINCE);
        assertThat(json.readTree(clusterHttp)).isEqualTo(json.readTree(cluster));
        jdbc.update("delete from incident_reviews where incident_id=?",second);
        jdbc.update("delete from incidents where id=?",second);
        var markerHttp = http(reader, CLOSE);
        assertThat(json.readTree(markerHttp)).isEqualTo(json.readTree(marker));
        Files.writeString(Path.of("target/maps-examples/incident.json"),markerHttp);
        Files.writeString(Path.of("target/maps-examples/cluster.json"),clusterHttp);
    }

    @Test
    void mapMigrationAndBothSpatialIndexesAreInstalled() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history "
                + "where success and version='20261110120000'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select indexname from pg_indexes where indexname like 'maps_%'",String.class))
                .containsExactlyInAnyOrder("maps_exact_location_idx","maps_public_location_idx","maps_validated_review_idx");
    }

    @Test
    void mapMigrationBuildsIndexesOverExistingDataAndWorksOutsideItsSchemaSearchPath() {
        UUID author = account(null);
        UUID id = incident(author,36.7213,-4.4214,"REPORTED",null);
        var base = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas("map_upgrade").defaultSchema("map_upgrade").target("3").load();
        base.migrate();
        try {
            jdbc.update("insert into map_upgrade.users select * from public.users where id=?",author);
            jdbc.update("insert into map_upgrade.incidents select * from public.incidents where id=?",id);
            var upgrade = org.flywaydb.core.Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                    .schemas("map_upgrade").defaultSchema("map_upgrade").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select latitude from map_upgrade.incidents where id=?",Double.class,id))
                    .isEqualTo(36.7213);
            assertThat(jdbc.queryForObject("select map_upgrade.map_in_malaga_v1(36.7213,-4.4214)",Boolean.class))
                    .isTrue();
            assertThat(jdbc.queryForObject("select map_upgrade.map_public_point_v1(36.7213,-4.4214) "
                    + "<@ map_upgrade.map_outline_v1()",Boolean.class)).isTrue();
        } finally {
            jdbc.execute("drop schema map_upgrade cascade");
        }
    }

    @Test
    void coastUsesOneStableBoundaryPointForThePublicCell() {
        var point = jdbc.queryForMap("""
                WITH v AS (SELECT unnest(map_vertices_v1()) AS p), coast AS (
                  SELECT p FROM v WHERE map_lat_v1(p) BETWEEN 36.65 AND 36.75
                    AND map_lon_v1(p) BETWEEN -4.6 AND -4.4
                    AND NOT (point((floor(p[0]/100)+0.5)*100,(floor(p[1]/100)+0.5)*100) <@ map_outline_v1())
                  LIMIT 1
                ), public AS (
                  SELECT p, map_public_point_v1(map_lat_v1(p),map_lon_v1(p)) AS q FROM coast
                ) SELECT q <@ map_outline_v1() AS inside, p <-> q AS distance,
                         q ~= map_public_point_v1(map_lat_v1(p),map_lon_v1(p)) AS stable FROM public
                """);
        assertThat(point.get("inside")).isEqualTo(true);
        assertThat(point.get("stable")).isEqualTo(true);
        assertThat(((Number)point.get("distance")).doubleValue()).isLessThan(142);
    }

    @Test
    void optionalCategoryAndPriorityAreMinimalAndRateLimitHasRetryAfter() throws Exception {
        UUID actor = account(null), category = UUID.randomUUID();
        jdbc.update("insert into categories(id,name) values(?,?)",category,"Map " + category);
        UUID id = incident(actor,36.7213,-4.4214,"REPORTED",category);
        jdbc.update("update incidents set priority='HIGH' where id=?",id);
        String token = bearer(actor);
        mvc.perform(request(CLOSE).header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category.id").value(category.toString()))
                .andExpect(jsonPath("$.items[0].category.name").value("Map " + category))
                .andExpect(jsonPath("$.items[0].category.description").doesNotExist())
                .andExpect(jsonPath("$.items[0].priority").value("HIGH"));
        boolean limited = false;
        for (int n=0;n<100;n++) {
            var response = mvc.perform(request(CLOSE).header("Authorization",token)).andReturn().getResponse();
            if (response.getStatus()==429) {
                assertThat(response.getHeader("Retry-After")).isNotBlank();
                assertThat(response.getContentType()).startsWith("application/problem+json");
                assertThat(response.getContentAsString()).contains("RATE_LIMITED");
                limited = true;
                break;
            }
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(limited).isTrue();
    }

    private UUID account(String globalRole) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users(id,display_name,email,password_hash,email_verified,created_at,updated_at)
                values(?,'Usuario sintético',?, ?,true,now(),now())
                """,id,id+"@example.org","x".repeat(60));
        jdbc.update("insert into user_roles values (?,'CITIZEN')",id);
        if (globalRole != null) jdbc.update("insert into user_roles values (?,?)",id,globalRole);
        return id;
    }

    private UUID incident(UUID owner,double lat,double lon,String state,UUID category) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into incidents(id,reporter_user_id,title,description,latitude,longitude,status,category_id,created_at,updated_at)
                values(?,?,'Título privado','Descripción privada',?,?,?,?,now(),now())
                """,id,owner,lat,lon,state,category);
        return id;
    }

    private void validate(UUID id,UUID operator) {
        jdbc.update("insert into incident_reviews(id,incident_id,reviewed_by_user_id,decision,reviewed_at) "
                + "values(?,?,?,'VALIDATE',now())",UUID.randomUUID(),id,operator);
    }

    private String bearer(UUID id) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("urbanpulse-tests").subject(id.toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("ver",0L)
                .claim("roles",List.of("ADMIN")).build(); // deliberately untrusted claim
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }

    private String http(UUID actor, MapQuery q) throws Exception {
        var uri = java.net.URI.create("http://127.0.0.1:" + port + URL + "?minLat=" + q.minLat()
                + "&minLon=" + q.minLon() + "&maxLat=" + q.maxLat() + "&maxLon=" + q.maxLon() + "&zoom=" + q.zoom());
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var response = client.send(java.net.http.HttpRequest.newBuilder(uri)
                    .header("Authorization",bearer(actor)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            return response.body();
        }
    }

    private MockHttpServletRequestBuilder request(MapQuery q) {
        return get(URL).param("minLat",Double.toString(q.minLat())).param("minLon",Double.toString(q.minLon()))
                .param("maxLat",Double.toString(q.maxLat())).param("maxLon",Double.toString(q.maxLon()))
                .param("zoom",Integer.toString(q.zoom()));
    }

    private MapQuery around(double lat,double lon) {
        return new MapQuery(lat-0.0000001,lon-0.0000001,lat+0.0000001,lon+0.0000001,18,null,null);
    }

    private boolean inside(double lat,double lon) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select map_in_malaga_v1(?,?)",Boolean.class,lat,lon));
    }
}
