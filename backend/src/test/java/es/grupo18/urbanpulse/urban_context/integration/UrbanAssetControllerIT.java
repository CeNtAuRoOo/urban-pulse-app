package es.grupo18.urbanpulse.urban_context.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import es.grupo18.urbanpulse.support.PostgisDePruebas;

import es.grupo18.urbanpulse.shared.mail.EmailSender;
import es.grupo18.urbanpulse.urban_context.model.UrbanAsset;
import es.grupo18.urbanpulse.urban_context.repository.UrbanAssetRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

/** HTTP real, JWT, Flyway y Hibernate validate sobre PostGIS efímero; sin DDL sustitutivo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class UrbanAssetControllerIT {
    @Container
    static final PostgreSQLContainer POSTGRES = PostgisDePruebas.nuevo();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UrbanAssetRepository assets;
    @Autowired JwtEncoder encoder;
    @Autowired JsonMapper json;
    @Autowired Flyway flyway;
    @Value("${local.server.port}") int port;
    @MockitoBean EmailSender emailSender;
    private UUID actor;

    @BeforeEach
    void prepararCatalogoAislado() {
        jdbc.execute("truncate cartografia.contexto_urbano restart identity");
        actor = cuenta(null);
    }

    @Test
    void instalaV1RealSinSemillasYValidaPostgisUuidv7EIndices() throws Exception {
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success", String.class))
                .containsExactly("1");
        assertThat(jdbc.queryForObject("select current_setting('server_version')", String.class)).startsWith("18.");
        assertThat(jdbc.queryForObject("select postgis_lib_version()", String.class)).startsWith("3.6");
        assertThat(jdbc.queryForObject("select uuidv7()", UUID.class).version()).isEqualTo(7);
        assertThat(jdbc.queryForObject("select Find_SRID('cartografia','contexto_urbano','geom')", Integer.class)).isEqualTo(25830);
        assertThat(jdbc.queryForList("select indexname from pg_indexes where schemaname='cartografia'", String.class))
                .contains("idx_contexto_urbano_geom", "idx_contexto_urbano_source_table");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        var response = get("", token(actor));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).isEmpty()).isTrue();
    }

    @Test
    void persisteYLeeJtsMedianteJpaYConsultaRepositorioConSearchPathPublic() throws Exception {
        var point = new GeometryFactory(new PrecisionModel(), 25830).createPoint(new Coordinate(372000, 4065000));
        var entity = assets.saveAndFlush(UrbanAsset.create("sintetico", "Activo de prueba", null, null,
                "Dirección privada sintética", "000000", "private@example.test", "https://example.test/private", point));
        var loaded = assets.findById(entity.getId()).orElseThrow();
        assertThat(loaded.getGeom().equalsExact(point)).isTrue();
        assertThat(loaded.getGeom().getSRID()).isEqualTo(25830);
        assertThat(jdbc.queryForObject("show search_path", String.class)).doesNotContain("cartografia");
        var origin = jdbc.queryForMap("select ST_X(g) lon, ST_Y(g) lat from "
                + "(select ST_Transform(geom,4326) g from cartografia.contexto_urbano where id=?) q", entity.getId());
        assertThat(assets.searchByProximity("sintetico", ((Number) origin.get("lon")).doubleValue(),
                ((Number) origin.get("lat")).doubleValue(), 1, 500)).extracting(UrbanAsset::getId).containsExactly(entity.getId());
        var response = get("?type=sintetico", token(actor));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        var item = json.readTree(response.body()).get(0);
        assertThat(item.size()).isEqualTo(10);
        assertThat(item.get("id").asLong()).isEqualTo(entity.getId());
        assertThat(item.get("geom").asString()).isEqualTo("POINT (372000 4065000)");
        for (String key : List.of("address", "phone", "email", "url")) assertThat(item.get(key).isNull()).isTrue();
        assertThat(response.body()).doesNotContain("private@example.test", "Dirección privada", "000000", "https://example.test/private");
        Path output = Path.of("target/urban-assets-examples/catalog.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, response.body());
    }

    @Test
    void limitaAmbasConsultasA500ConOrdenDeterministaYFiltroReal() throws Exception {
        jdbc.update("insert into cartografia.contexto_urbano(source_table,geom) "
                + "select 'sintetico', ST_Transform(ST_SetSRID(ST_MakePoint(-4.4214,36.7213),4326),25830) "
                + "from generate_series(1,520)");
        jdbc.update("insert into cartografia.contexto_urbano(source_table,geom) "
                + "values ('otro', ST_SetSRID(ST_MakePoint(0,0),25830))");
        for (String query : List.of("", "?type=sintetico", "?latitude=36.7213&longitude=-4.4214&radiusMeters=1")) {
            var response = get(query, token(actor));
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode rows = json.readTree(response.body());
            assertThat(rows.size()).isEqualTo(500);
            for (int i = 0; i < 500; i++) assertThat(rows.get(i).get("id").asLong()).isEqualTo(i + 1);
            assertThat(get(query, token(actor)).body()).isEqualTo(response.body());
        }
        assertThat(json.readTree(get("?type=otro", token(actor)).body()).size()).isEqualTo(1);
        assertThat(json.readTree(get("?type=ausente", token(actor)).body()).size()).isZero();
        assertThat(json.readTree(get("?type=%27%20OR%201%3D1--", token(actor)).body()).size()).isZero();
    }

    @Test
    void proximidadUsaMetrosGeometriasCompletasYDistanciaAntesDelId() throws Exception {
        String center = "ST_Transform(ST_SetSRID(ST_MakePoint(-4.4214,36.7213),4326),25830)";
        for (double distance : new double[] {100.01, 100, 20, 0}) {
            jdbc.update("insert into cartografia.contexto_urbano(source_table,geom) values ('sintetico',ST_Translate(" + center + ",?,0))", distance);
        }
        jdbc.update("insert into cartografia.contexto_urbano(source_table,geom) values ('linea', "
                + "ST_MakeLine(ST_Translate(" + center + ",-1000,0),ST_Translate(" + center + ",1000,0)))");
        jdbc.update("insert into cartografia.contexto_urbano(source_table,geom) values ('poligono',ST_Buffer(" + center + ",10))");
        var rows = json.readTree(get("?latitude=36.7213&longitude=-4.4214&radiusMeters=100", token(actor)).body());
        assertThat(rows.size()).isEqualTo(5);
        assertThat(rows.get(0).get("id").asLong()).isEqualTo(4);
        assertThat(rows.get(1).get("geom").asString()).startsWith("LINESTRING");
        assertThat(rows.get(2).get("geom").asString()).startsWith("POLYGON");
        assertThat(rows.get(3).get("id").asLong()).isEqualTo(3);
        assertThat(rows.get(4).get("id").asLong()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude=NaN&longitude=0&radiusMeters=1", "latitude=Infinity&longitude=0&radiusMeters=1",
            "latitude=0&longitude=-Infinity&radiusMeters=1", "latitude=0&longitude=NaN&radiusMeters=1",
            "latitude=0&longitude=0&radiusMeters=NaN", "latitude=0&longitude=0&radiusMeters=Infinity",
            "latitude=91&longitude=0&radiusMeters=1", "latitude=0&longitude=181&radiusMeters=1",
            "latitude=0&longitude=0&radiusMeters=0", "latitude=0&longitude=0&radiusMeters=5001",
            "latitude=", "latitude=&longitude=&radiusMeters=", "radiusMeters=%20",
            "latitude=36", "longitude=-4&radiusMeters=100", "latitude=abc", "type=origen%00privado"})
    void rechazaEntradasAdversariasConProblemDetail400(String query) throws Exception {
        var response = get("?" + query, token(actor));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(json.readTree(response.body()).get("status").asInt()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("org.postgresql", "SELECT ", "cartografia");
    }

    @Test
    void admiteLimitesYTipoUnicodeSinInventarResultados() throws Exception {
        for (String query : List.of("?latitude=90&longitude=180&radiusMeters=5000",
                "?latitude=-90&longitude=-180&radiusMeters=1", "?type=%20%20", "?type=%C3%81rboles%20%F0%9F%8C%B3")) {
            assertThat(get(query, token(actor)).statusCode()).isEqualTo(200);
        }
        assertThat(get("?type=" + "x".repeat(65), token(actor)).statusCode()).isEqualTo(400);
    }

    @Test
    void exigeIdentidadVerificacionYVigenciaSinPrivilegiosMunicipales() throws Exception {
        assertThat(get("", null).statusCode()).isEqualTo(401);
        assertThat(get("", "Bearer invalido").statusCode()).isEqualTo(401);
        String token = token(actor);
        jdbc.update("update users set email_verified=false where id=?", actor);
        assertThat(get("", token).statusCode()).isEqualTo(403);
        jdbc.update("update users set email_verified=true, active=false where id=?", actor);
        assertThat(get("", token).statusCode()).isEqualTo(401);
        jdbc.update("update users set active=true, token_version=1 where id=?", actor);
        assertThat(get("", token).statusCode()).isEqualTo(401);
        for (String role : List.of("CITIZEN", "OPERATOR", "ADMIN")) {
            assertThat(get("", token(cuenta(role))).statusCode()).isEqualTo(200);
        }
        var unavailable = request("/api/v1/urban-assets/123", token(cuenta(null)));
        assertThat(unavailable.statusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL", "ST_GeomFromText('POINT EMPTY',25830)",
            "ST_GeomFromText('POLYGON ((0 0,2 2,2 0,0 2,0 0))',25830)",
            "ST_GeomFromText('POINT (NaN 0)',25830)", "ST_SetSRID(ST_MakePoint('Infinity'::float8,0),25830)",
            "ST_GeomFromText('POINT Z (0 0 1)',25830)", "ST_GeomFromText('POINT (0 0)',4326)"})
    void baseDeDatosRechazaGeometriasQueNoPuedePublicar(String expression) {
        assertThatThrownBy(() -> jdbc.update("insert into cartografia.contexto_urbano(source_table,geom) values ('sintetico'," + expression + ")"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(assets.count()).isZero();
    }

    @Test
    void exigeIdentidadPositivaYOrigenConsultableTambienEnInsercionesSql() {
        String sql = "insert into cartografia.contexto_urbano(id,source_table,geom) "
                + "values (?,?,ST_SetSRID(ST_MakePoint(0,0),25830))";
        assertThatThrownBy(() -> jdbc.update(sql, -1, "origen")).isInstanceOf(DataIntegrityViolationException.class);
        for (String origen : List.of("", " origen", " origen", "ori\ngen")) {
            assertThatThrownBy(() -> jdbc.update(sql, 1, origen)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void compruebaPlanesEspacialesYConservaFuncionesNativasDeMapas() {
        jdbc.execute("insert into cartografia.contexto_urbano(source_table,geom) "
                + "select 'sintetico',ST_SetSRID(ST_MakePoint(g*100,4000000),25830) from generate_series(1,3000) g");
        jdbc.execute("analyze cartografia.contexto_urbano");
        String plan = String.join("\n", jdbc.queryForList("explain select id from cartografia.contexto_urbano "
                + "where ST_DWithin(geom,ST_SetSRID(ST_MakePoint(1000,4000000),25830),1) order by id limit 500", String.class));
        assertThat(plan).contains("idx_contexto_urbano_geom");
        assertThat(jdbc.queryForObject("select pg_typeof(map_xy_v1(36.7213,-4.4214))::text", String.class)).isEqualTo("point");
        assertThat(jdbc.queryForObject("select map_in_malaga_v1(36.7213,-4.4214)", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select ST_Distance(ST_SetSRID(ST_MakePoint(0,0),25830),ST_SetSRID(ST_MakePoint(300,0),25830))", Double.class)).isEqualTo(300);
    }

    private UUID cuenta(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(id,display_name,email,password_hash,email_verified,created_at,updated_at) "
                + "values (?,'Cuenta sintética',?,repeat('x',60),true,now(),now())", id, id + "@example.test");
        jdbc.update("insert into user_roles values (?,'CITIZEN')", id);
        if (role != null && !role.equals("CITIZEN")) jdbc.update("insert into user_roles values (?,?)", id, role);
        return id;
    }

    private String token(UUID id) {
        Instant now = Instant.now();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("urbanpulse-tests").subject(id.toString()).issuedAt(now)
                        .expiresAt(now.plusSeconds(600)).claim("ver", 0).build())).getTokenValue();
    }

    private HttpResponse<String> get(String query, String token) throws Exception {
        return request("/api/v1/urban-assets" + query, token);
    }

    private HttpResponse<String> request(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", token);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
