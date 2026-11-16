package es.grupo18.urbanpulse.urban_context.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;
import es.grupo18.urbanpulse.urban_context.model.UrbanAsset;
import es.grupo18.urbanpulse.urban_context.repository.UrbanAssetRepository;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración end-to-end: HTTP → service → repository → PostGIS → JSON.
 * La autenticación se simula con {@code jwt()} porque el filtro real no
 * necesita un emisor JWT en este slice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UrbanAssetControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGIS = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("urbanpulse_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGIS::getJdbcUrl);
        r.add("spring.datasource.username", POSTGIS::getUsername);
        r.add("spring.datasource.password", POSTGIS::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.spatial.dialect.postgis.PostgisPG10Dialect");
    }

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 25830);
    private static final double CENTER_X = 239_000.0;
    private static final double CENTER_Y = 4_140_000.0;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UrbanAssetRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.save(UrbanAsset.create("Ferrocarriles", "Centro", null, null, null,
                null, null, null, GF.createPoint(new Coordinate(CENTER_X, CENTER_Y))));
        repository.save(UrbanAsset.create("Ferrocarriles", "Cerca", null, null, null,
                null, null, null, GF.createPoint(new Coordinate(CENTER_X + 300, CENTER_Y))));
        repository.save(UrbanAsset.create("Aparcamientos", "Lejos", null, null, null,
                null, null, null, GF.createPoint(new Coordinate(CENTER_X + 2000, CENTER_Y))));
    }

    // --- Feliz ------------------------------------------------------------

    @Test
    void proximitySearchReturnsAssetsWithinRadiusWithWkt() throws Exception {
        mvc.perform(get("/api/v1/urban-assets")
                .param("latitude", "37.3891")
                .param("longitude", "-5.9845")
                .param("radiusMeters", "500")
                .with(citizen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.name=='Centro')]").exists())
                .andExpect(jsonPath("$[?(@.name=='Cerca')]").exists())
                .andExpect(jsonPath("$[?(@.name=='Lejos')]").doesNotExist())
                .andExpect(jsonPath("$[*].geom", everyItem(notNullValue())));
    }

    @Test
    void typeFilterCombinesWithProximity() throws Exception {
        mvc.perform(get("/api/v1/urban-assets")
                .param("type", "Aparcamientos")
                .param("latitude", "37.3891")
                .param("longitude", "-5.9845")
                .param("radiusMeters", "5000")
                .with(citizen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Lejos"))
                .andExpect(jsonPath("$[0].geom").isNotEmpty());
    }

    @Test
    void typeOnlyFilterWorksWithoutProximity() throws Exception {
        mvc.perform(get("/api/v1/urban-assets")
                .param("type", "Ferrocarriles")
                .with(citizen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].geom", everyItem(notNullValue())));
    }

    // --- Seguridad --------------------------------------------------------

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/urban-assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- Errores ----------------------------------------------------------

    @Test
    void outOfRangeRadiusReturns400WithCode() throws Exception {
        mvc.perform(get("/api/v1/urban-assets")
                .param("latitude", "37.3891")
                .param("longitude", "-5.9845")
                .param("radiusMeters", "6000")
                .with(citizen()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RADIUS"));
    }

    @Test
    void partialProximityReturns400WithCode() throws Exception {
        mvc.perform(get("/api/v1/urban-assets")
                .param("latitude", "37.3891")
                .with(citizen()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INCOMPLETE_PROXIMITY_PARAMETERS"));
    }

    // --- Helpers ----------------------------------------------------------

    private static org.springframework.test.web.servlet.request.RequestPostProcessor citizen() {
        return jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CITIZEN"),
                new SimpleGrantedAuthority(DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED));
    }
}
