package es.grupo18.urbanpulse.incidents.integration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.audit.model.AuditEvent;
import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.auth.dto.RegisterRequest;
import es.grupo18.urbanpulse.auth.dto.TokenDTO;
import es.grupo18.urbanpulse.auth.service.AuthService;
import es.grupo18.urbanpulse.auth.service.EmailVerificationCleanup;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.api.IncidentETags;
import es.grupo18.urbanpulse.incidents.dto.CoordinatesDTO;
import es.grupo18.urbanpulse.incidents.dto.CreateIncidentRequest;
import es.grupo18.urbanpulse.incidents.dto.IncidentDTO;
import es.grupo18.urbanpulse.incidents.dto.UpdateIncidentRequest;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.model.IncidentPriority;
import es.grupo18.urbanpulse.incidents.model.IncidentReview;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.incidents.model.PriorityDecision;
import es.grupo18.urbanpulse.incidents.model.PrioritySource;
import es.grupo18.urbanpulse.incidents.model.ReviewDecision;
import es.grupo18.urbanpulse.incidents.model.StatusChange;
import es.grupo18.urbanpulse.incidents.repository.IncidentRepository;
import es.grupo18.urbanpulse.incidents.repository.IncidentReviewRepository;
import es.grupo18.urbanpulse.incidents.repository.PriorityDecisionRepository;
import es.grupo18.urbanpulse.incidents.repository.StatusChangeRepository;
import es.grupo18.urbanpulse.incidents.service.IncidentService;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.mail.EmailSender;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class IncidentPostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;
    @Autowired
    IncidentService service;
    @Autowired
    IncidentRepository incidents;
    @Autowired
    StatusChangeRepository transitions;
    @Autowired
    IncidentReviewRepository reviews;
    @Autowired
    PriorityDecisionRepository priorities;
    @Autowired
    AuditEventRepository audit;
    @Autowired
    AuthService auth;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    EmailVerificationCleanup cleanup;
    @MockitoBean
    EmailSender emailSender;
    private static final String URL = "/api/v1/incidents";
    private static final String BODY = """
            {"title":"  Bache grande  ","description":"  Pavimento deteriorado  ",
             "coordinates":{"latitude":36.7,"longitude":-4.4}}
            """;

    @BeforeEach
    void isolateIncidentFixtures() {
        jdbc.execute("truncate status_changes, incident_reviews, priority_decisions, incidents, audit_events");
    }

    @Test
    void etagTracksChangesToProjectedResources() throws Exception {
        var owner = account(null);
        var admin = account("ADMIN");
        var categoryResponse = mvc.perform(post("/api/v1/categories")
                .header("Authorization", bearer(admin)).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Categoría " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        UUID category = UUID.fromString(json.readTree(categoryResponse.getContentAsString()).get("id").asString());
        var incident = service.create(owner.user().id(), 0, new CreateIncidentRequest("Aviso válido",
                "Descripción del aviso", new CoordinatesDTO(0.0, 0.0, null), category));
        String path = URL + "/" + incident.id();
        var before = mvc.perform(get(path).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer(owner))
                .contentType(APPLICATION_JSON).content("{\"displayName\":\"Nombre actualizado\"}"))
                .andExpect(status().isOk());
        var renamed = mvc.perform(get(path).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reporter.displayName").value("Nombre actualizado"))
                .andReturn().getResponse();
        mvc.perform(patch("/api/v1/categories/" + category).header("Authorization", bearer(admin))
                .contentType(APPLICATION_JSON).content("{\"name\":\"Renombrada " + category
                        + "\",\"description\":\"Descripción nueva\",\"active\":false}"))
                .andExpect(status().isOk());
        var recategorized = mvc.perform(get(path).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category.active").value(false))
                .andReturn().getResponse();
        org.assertj.core.api.SoftAssertions.assertSoftly(check -> {
            check.assertThat(renamed.getContentAsByteArray()).isNotEqualTo(before.getContentAsByteArray());
            check.assertThat(renamed.getHeader("ETag")).isNotEqualTo(before.getHeader("ETag"));
            check.assertThat(recategorized.getContentAsByteArray()).isNotEqualTo(renamed.getContentAsByteArray());
            check.assertThat(recategorized.getHeader("ETag")).isNotEqualTo(renamed.getHeader("ETag"));
        });
        for (String oldTag : List.of(before.getHeader("ETag"), renamed.getHeader("ETag"))) {
            mvc.perform(patch(path).header("Authorization", bearer(owner)).header("If-Match", oldTag)
                    .contentType(APPLICATION_JSON).content("{\"title\":\"Otro título\"}"))
                    .andExpect(status().isPreconditionFailed());
        }
        mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer(owner))
                .contentType(APPLICATION_JSON).content("{\"displayName\":\"Nombre actualizado\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isOk())
                .andExpect(header().string("ETag", recategorized.getHeader("ETag")));
        mvc.perform(patch(path).header("Authorization", bearer(owner))
                .header("If-Match", recategorized.getHeader("ETag")).contentType(APPLICATION_JSON)
                .content("{\"title\":\"Aviso válido\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", recategorized.getHeader("ETag")));
        assertThat(service.get(owner.user().id(), 0, incident.id()).version()).isZero();
    }

    @Test
    void incidentBodiesAcceptUnicodeAtTheByteLimitAndRejectExcess() throws Exception {
        var owner = account(null);
        String unicodeBody = BODY.replace("Pavimento deteriorado", "🚦".repeat(5000));
        int bytes = unicodeBody.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String boundary = unicodeBody + " ".repeat(65_536 - bytes);
        var created = mvc.perform(post(URL).header("Authorization", bearer(owner)).contentType(APPLICATION_JSON)
                .content(boundary)).andExpect(status().isCreated()).andReturn().getResponse();
        String path = created.getHeader("Location");
        String escaped = "{\"description\":\"" + "\\uD83D\\uDEA6".repeat(5000) + "\"}";
        mvc.perform(patch(path).header("Authorization", bearer(owner)).header("If-Match", created.getHeader("ETag"))
                .contentType(APPLICATION_JSON).content(escaped)).andExpect(status().isOk());
        String patchBody = "{\"title\":\"Bache grande\"}";
        patchBody += " ".repeat(65_536 - patchBody.length());
        mvc.perform(patch(path).header("Authorization", bearer(owner)).header("If-Match", created.getHeader("ETag"))
                .contentType(APPLICATION_JSON).content(patchBody)).andExpect(status().isOk());
        mvc.perform(patch(path).header("Authorization", bearer(owner)).header("If-Match", created.getHeader("ETag"))
                .contentType(APPLICATION_JSON).content(patchBody + " ")).andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        mvc.perform(post(URL).header("Authorization", bearer(owner)).contentType(APPLICATION_JSON)
                .content(boundary + " ")).andExpect(status().is(413));
    }

    @Test
    void realHttpChunkedBodyCannotBypassLimit() throws Exception {
        var owner = account(null);
        String body = BODY + " ".repeat(65_536);
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + URL))
                    .header("Authorization", bearer(owner)).header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(
                            body.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                    .build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
        }
        assertThat(service.mine(owner.user().id(), 0, 1, 20).data()).isEmpty();
    }

    @Test
    void createDetailAndPatchExposeOnlyRealDataAndAtomicHistory() throws Exception {
        var owner = account(null);
        var response = mvc
                .perform(post(URL).header("Authorization", bearer(owner)).contentType(APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.title").value("Bache grande"))
                .andExpect(jsonPath("$.reporter.id").value(owner.user().id().toString()))
                .andExpect(jsonPath("$.reporter.email").doesNotExist())
                .andExpect(jsonPath("$.reporter.roles").doesNotExist())
                .andExpect(jsonPath("$.category").doesNotExist()).andExpect(jsonPath("$.priority").doesNotExist())
                .andExpect(jsonPath("$.attachmentCount").doesNotExist())
                .andExpect(jsonPath("$.contextStatus").doesNotExist())
                .andExpect(jsonPath("$.location.geocodingStatus").doesNotExist()).andReturn().getResponse();
        String location = response.getHeader("Location");
        UUID id = UUID.fromString(json.readTree(response.getContentAsString()).get("id").asString());
        assertThat(id.version()).isEqualTo(7);
        String initialTag = response.getHeader("ETag");
        assertThat(initialTag).isEqualTo("\"sha256-" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(response.getContentAsByteArray())) + "\"");
        mvc.perform(get(location).header("Authorization", bearer(owner))).andExpect(status().isOk())
                .andExpect(header().string("ETag", initialTag));
        mvc.perform(get(location).header("Authorization", bearer(owner)).accept("application/json;charset=UTF-16"))
                .andExpect(status().isNotAcceptable());
        var corrected = mvc
                .perform(patch(location).header("Authorization", bearer(owner)).header("If-Match", initialTag)
                        .contentType(APPLICATION_JSON).content("{\"title\":\"Bache corregido\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String updatedTag = corrected.getHeader("ETag");
        assertThat(updatedTag).isNotEqualTo(initialTag);
        mvc.perform(patch(location).header("Authorization", bearer(owner)).header("If-Match", initialTag)
                .contentType(APPLICATION_JSON).content("{\"title\":\"Bache corregido\"}"))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(patch(location).header("Authorization", bearer(owner)).header("If-Match", updatedTag)
                .contentType(APPLICATION_JSON).content("{\"title\":\" Bache corregido \"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", updatedTag));
        mvc.perform(get(location + "/history").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.data[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$.data[1].eventType").value("UPDATED"));
        assertThat(count("status_changes", "incident_id", id)).isZero();
        assertThat(count("incident_reviews", "incident_id", id)).isZero();
        assertThat(count("priority_decisions", "incident_id", id)).isZero();
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class))
                .containsExactly("1", "2", "3", "4");
    }

    @Test
    void categoryNullOmissionInactiveHistoricalAndReplacementCoordinates() throws Exception {
        var owner = account(null);
        UUID category = jdbc.queryForObject("insert into categories(name) values (?) returning id", UUID.class,
                UUID.randomUUID().toString());
        var created = service.create(owner.user().id(), 0, new CreateIncidentRequest("Aviso válido",
                "Descripción del aviso", new CoordinatesDTO(0.0, 0.0, 5.0), category));
        jdbc.update("update categories set active=false where id=?", category);
        var patch = new UpdateIncidentRequest();
        patch.setTitle("Otro título");
        assertThat(
                service.patch(owner.user().id(), 0, created.id(), tag(owner, created.id()), patch).category().active())
                .isFalse();
        patch.setCategoryId(category);
        assertThatThrownBy(() -> service.patch(owner.user().id(), 0, created.id(), tag(owner, created.id()), patch))
                .isInstanceOf(ApiException.class);
        mvc.perform(patch(URL + "/" + created.id()).header("Authorization", bearer(owner))
                .header("If-Match", tag(owner, created.id()))
                .contentType(APPLICATION_JSON)
                .content("{\"categoryId\":null,\"coordinates\":{\"latitude\":90,\"longitude\":-180}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.location.coordinates.accuracyMeters").doesNotExist());
        for (UUID invalid : List.of(category, UUID.randomUUID())) {
            assertThatThrownBy(() -> service.create(owner.user().id(), 0,
                    new CreateIncidentRequest("Aviso válido", "Descripción del aviso",
                            new CoordinatesDTO(0.0, 0.0, null), invalid)))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void securityRealJwtOwnershipAndDepartmentRolesNeverGrantGlobalAccess() throws Exception {
        var owner = account(null);
        var other = account(null);
        var incident = create(owner);
        String path = URL + "/" + incident.id();
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        for (String role : List.of("TECHNICIAN", "DEPARTMENT_COORDINATOR")) {
            UUID department = jdbc.queryForObject("insert into departments(name,code) values (?,?) returning id",
                    UUID.class,
                    UUID.randomUUID().toString(), "D" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
            jdbc.update(
                    "insert into department_memberships(id,user_id,department_id,role_code,joined_at) values (uuidv7(),?,?,?,now())",
                    other.user().id(), department, role);
        }
        for (String suffix : List.of("", "/history")) {
            mvc.perform(get(path + suffix).header("Authorization", bearer(other))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));
        }
        mvc.perform(patch(path).header("Authorization", bearer(other)).contentType(APPLICATION_JSON)
                .content("{\"title\":\"Cambio ajeno\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(URL).header("Authorization", bearer(other))).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/mine").header("Authorization", bearer(other))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(0));
        for (String role : List.of("OPERATOR", "ADMIN")) {
            var staff = account(role);
            mvc.perform(get(path).header("Authorization", bearer(staff))).andExpect(status().isOk());
            mvc.perform(get(path + "/history").header("Authorization", bearer(staff))).andExpect(status().isOk());
            mvc.perform(get(URL).header("Authorization", bearer(staff))).andExpect(status().isOk());
            assertThat(service
                    .patch(staff.user().id(), 0, incident.id(), tag(staff, incident.id()),
                            patchTitle("Edición " + role))
                    .title()).contains(role);
            incident = service.get(owner.user().id(), 0, incident.id());
        }
        jdbc.update("update users set email_verified=false where id=?", owner.user().id());
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        jdbc.update("update users set email_verified=true, active=false where id=?", owner.user().id());
        mvc.perform(get(path).header("Authorization", bearer(owner))).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> service.get(owner.user().id(), 0, incidentId(path))).isInstanceOf(ApiException.class);
    }

    @Test
    void preconditionsStateAndMalformedHeadersHaveDocumentedStatuses() throws Exception {
        var owner = account(null);
        var incident = create(owner);
        String path = URL + "/" + incident.id();
        mvc.perform(patch(path).header("Authorization", bearer(owner)).contentType(APPLICATION_JSON)
                .content("{\"title\":\"Cambio válido\"}"))
                .andExpect(status().is(428));
        for (String tag : List.of("*", "W/\"0\"", "0", "\"0\",\"1\"", "\"9223372036854775808\"")) {
            mvc.perform(patch(path).header("Authorization", bearer(owner)).header("If-Match", tag)
                    .contentType(APPLICATION_JSON).content("{\"title\":\"Cambio válido\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));
        }
        mvc.perform(patch(path).header("Authorization", bearer(owner))
                .header("If-Match", "\"sha256-" + "0".repeat(64) + "\"")
                .contentType(APPLICATION_JSON).content("{\"title\":\"Cambio válido\"}"))
                .andExpect(status().isPreconditionFailed());
        String currentTag = tag(owner, incident.id());
        mvc.perform(patch(path).header("Authorization", bearer(owner)).header("If-Match", currentTag, currentTag)
                .contentType(APPLICATION_JSON).content("{\"title\":\"Cambio válido\"}"))
                .andExpect(status().isBadRequest());
        jdbc.update("update incidents set status='VALIDATED' where id=?", incident.id());
        mvc.perform(patch(path).header("Authorization", bearer(owner)).header("If-Match", tag(owner, incidentId(path)))
                .contentType(APPLICATION_JSON).content("{\"title\":\"Cambio válido\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void strictJsonAndPatchPresenceValidation() throws Exception {
        var owner = account(null);
        String path = URL + "/" + create(owner).id();
        for (String body : List.of("{\"title\":null}", "{\"description\":null}", "{\"coordinates\":null}",
                "{\"title\":3}", "{\"title\":\"Primero\",\"title\":\"Segundo\"}", "{} {}", "null")) {
            mvc.perform(
                    patch(path).header("Authorization", bearer(owner)).header("If-Match", tag(owner, incidentId(path)))
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "{\"status\":\"RESOLVED\"}", "{\"reporter\":null}", "{\"priority\":null}",
                "{\"department\":null}", "{\"categoryPresent\":true}", "{\"title\":\"abc\"}",
                "{\"coordinates\":{}}", "{\"coordinates\":{\"latitude\":91,\"longitude\":0}}",
                "{\"coordinates\":{\"latitude\":0,\"longitude\":0,\"accuracyMeters\":-1}}",
                "{\"coordinates\":{\"latitude\":0,\"longitude\":0,\"unknown\":1}}")) {
            mvc.perform(
                    patch(path).header("Authorization", bearer(owner)).header("If-Match", tag(owner, incidentId(path)))
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableContent());
        }
        for (String body : List.of("{}", BODY.replace("Bache grande", "a"), BODY.replace("36.7", "null"),
                BODY.replace("36.7", "1e999"), BODY.replace("Pavimento deteriorado", " "))) {
            mvc.perform(post(URL).header("Authorization", bearer(owner)).contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableContent());
        }
        mvc.perform(post(URL).header("Authorization", bearer(owner)).contentType(APPLICATION_JSON)
                .content(BODY.replace("36.7", "\"36.7\""))).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/not-a-uuid").header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
    }

    @Test
    void sqlPaginationFiltersStableOrderAndUnknownQueries() throws Exception {
        var owner = account(null);
        var admin = account("ADMIN");
        var first = create(owner);
        var second = create(owner);
        var control = create(admin);
        jdbc.update("update incidents set created_at='2026-01-02',updated_at='2026-01-02' where id=?", control.id());
        jdbc.update("update incidents set created_at='2026-01-01',updated_at='2026-01-01' where reporter_user_id=?",
                owner.user().id());
        var page = service.mine(owner.user().id(), 0, 1, 1);
        assertThat(page.pagination().totalItems()).isEqualTo(2);
        List<UUID> expected = java.util.stream.Stream.of(first.id(), second.id())
                .sorted(java.util.Comparator.comparing((UUID id) -> id.toString()).reversed()).toList();
        assertThat(page.data().getFirst().id()).isEqualTo(expected.getFirst());
        assertThat(service.mine(owner.user().id(), 0, 2, 1).data().getFirst().id()).isEqualTo(expected.getLast());
        assertThat(service.mine(owner.user().id(), 0, 3, 1).data()).isEmpty();
        assertThat(service.list(admin.user().id(), 0, 1, 20, IncidentStatus.REPORTED, null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")).data()).hasSize(2);
        assertThat(service.list(admin.user().id(), 0, 1, 20, IncidentStatus.RESOLVED, null, null, null).data())
                .isEmpty();
        assertThat(service.list(admin.user().id(), 0, 1, 20, null, UUID.randomUUID(), null, null).data()).isEmpty();
        UUID category = jdbc.queryForObject("insert into categories(name) values (?) returning id", UUID.class,
                UUID.randomUUID().toString());
        jdbc.update("update incidents set category_id=? where id=?", category, first.id());
        jdbc.update("update incidents set category_id=?,status='VALIDATED' where id=?", category, control.id());
        assertThat(service.list(admin.user().id(), 0, 1, 20, null, null, null, null).data())
                .extracting(item -> item.id()).containsExactly(control.id(), expected.getFirst(), expected.getLast());
        assertThat(service.list(admin.user().id(), 0, 1, 20, null, category, null, null).data()).hasSize(2);
        assertThat(service.list(admin.user().id(), 0, 1, 20, IncidentStatus.REPORTED, category,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")).data())
                .extracting(item -> item.id()).containsExactly(first.id());
        assertThat(service.list(admin.user().id(), 0, 1, 20, IncidentStatus.VALIDATED, category,
                null, Instant.parse("2026-01-01T00:00:00Z")).data()).isEmpty();
        assertThat(service.list(admin.user().id(), 0, 1, 20, null, null,
                Instant.parse("2026-01-02T00:00:00Z"), null).data())
                .extracting(item -> item.id()).containsExactly(control.id());
        for (String query : List.of("page=0", "pageSize=101", "page=2147483647&pageSize=100",
                "userId=" + admin.user().id())) {
            mvc.perform(get(URL + "/mine?" + query).header("Authorization", bearer(owner)))
                    .andExpect(status().isBadRequest());
        }
        for (String query : List.of("sort=priority", "districtId=x", "status=WRONG", "createdFrom=bad",
                "createdFrom=2026-02-01T00:00:00Z&createdTo=2026-01-01T00:00:00Z")) {
            mvc.perform(get(URL + "?" + query).header("Authorization", bearer(admin)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void historyFiltersBeforePaginationAndDoesNotExposeArbitraryDetails() {
        var owner = account(null);
        var incident = create(owner);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            User user = em.getReference(User.class, owner.user().id());
            audit.saveAndFlush(new AuditEvent(user, "INTERNAL", "INCIDENT", incident.id().toString(),
                    Map.of("private", "hidden"), Instant.now()));
            audit.saveAndFlush(new AuditEvent(user, "INCIDENT_UPDATED", "OTHER", incident.id().toString(), Map.of(),
                    Instant.now()));
            audit.saveAndFlush(new AuditEvent(user, "INCIDENT_UPDATED", "INCIDENT", incident.id().toString(),
                    Map.of("private", "hidden", "version", "1"), incident.createdAt()));
        });
        var page = service.history(owner.user().id(), 0, incident.id(), 1, 1);
        assertThat(page.pagination().totalItems()).isEqualTo(2);
        var next = service.history(owner.user().id(), 0, incident.id(), 2, 1);
        assertThat(next.data().getFirst().details()).containsOnlyKeys("version");
        List<UUID> ids = jdbc.queryForList(
                "select id from audit_events where resource_type='INCIDENT' and resource_id=? and action in ('INCIDENT_CREATED','INCIDENT_UPDATED') order by occurred_at,id",
                UUID.class, incident.id().toString());
        assertThat(List.of(page.data().getFirst().id(), next.data().getFirst().id())).containsExactlyElementsOf(ids);
    }

    @Test
    void auditFailureRollsBackCreateAndPatch() {
        var owner = account(null);
        var incident = create(owner);
        jdbc.execute(
                "alter table audit_events add constraint test_fail_incident_audit check (action not in ('INCIDENT_CREATED','INCIDENT_UPDATED')) not valid");
        try {
            assertThatThrownBy(() -> create(owner)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> service.patch(owner.user().id(), 0, incident.id(), tag(owner, incident.id()),
                    patchTitle("Otro título")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("alter table audit_events drop constraint test_fail_incident_audit");
        }
        assertThat(service.mine(owner.user().id(), 0, 1, 20).pagination().totalItems()).isEqualTo(1);
        assertThat(service.get(owner.user().id(), 0, incident.id()).version()).isZero();
        assertThat(service.history(owner.user().id(), 0, incident.id(), 1, 20).pagination().totalItems()).isEqualTo(1);
    }

    @Test
    void concurrentServiceCorrectionsHaveOneWinnerAndOneAuditEvent() throws Exception {
        var owner = account(null);
        var operator = account("OPERATOR");
        var incident = create(owner);
        String expectedTag = tag(owner, incident.id());
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(owner, operator).stream().map(actor -> executor.submit(() -> {
                try {
                    return new TransactionTemplate(transactions).execute(tx -> {
                        incidents.findById(incident.id()).orElseThrow();
                        try {
                            barrier.await(15, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        service.patch(actor.user().id(), 0, incident.id(), expectedTag,
                                patchTitle("Corregido " + actor.user().id()));
                        return 200;
                    });
                } catch (ApiException e) {
                    return e.status().value();
                }
            })).toList();
            assertThat(List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 412);
        }
        assertThat(service.get(owner.user().id(), 0, incident.id()).version()).isEqualTo(1);
        assertThat(service.history(owner.user().id(), 0, incident.id(), 1, 20).pagination().totalItems()).isEqualTo(2);
    }

    @Test
    void structuralEntitiesRoundTripAndSqlConstraintsPreserveHistory() {
        var owner = account(null);
        var incident = create(owner);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Incident entity = incidents.findById(incident.id()).orElseThrow();
            User user = em.getReference(User.class, owner.user().id());
            var change = transitions.saveAndFlush(new StatusChange(entity, IncidentStatus.REPORTED,
                    IncidentStatus.VALIDATED, user, null, null, Instant.now()));
            var review = reviews.saveAndFlush(
                    new IncidentReview(entity, user, ReviewDecision.REJECT, "Motivo válido", Instant.now()));
            var priority = priorities.saveAndFlush(new PriorityDecision(entity, user, IncidentPriority.HIGH,
                    PrioritySource.MANUAL, "Riesgo vial", Instant.now()));
            assertThat(List.of(change.getId().version(), review.getId().version(), priority.getId().version()))
                    .containsOnly(7);
            em.clear();
            assertThat(transitions.findById(change.getId()).orElseThrow().getFromStatus())
                    .isEqualTo(IncidentStatus.REPORTED);
            assertThat(reviews.findById(review.getId()).orElseThrow().getDecision()).isEqualTo(ReviewDecision.REJECT);
            assertThat(priorities.findById(priority.getId()).orElseThrow().getSource())
                    .isEqualTo(PrioritySource.MANUAL);
        });
        for (String change : List.of("latitude=91", "longitude=-181", "latitude='NaN'", "accuracy_meters='Infinity'",
                "accuracy_meters=-1",
                "status='INVALID'", "priority='INVALID'", "title=null", "title='abcd'", "title=' texto con bordes '",
                "description=repeat('x',5001)", "version=-1",
                "reporter_user_id='00000000-0000-0000-0000-000000000000'")) {
            assertThatThrownBy(() -> jdbc.update("update incidents set " + change + " where id=?", incident.id()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("delete from users where id=?", owner.user().id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from incidents where id=?", incident.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.get(owner.user().id(), 0, incident.id()).status()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(service.get(owner.user().id(), 0, incident.id()).priority()).isNull();
    }

    @Test
    void additiveMigrationValidatesExistingV2DataWithoutRewritingHistory() {
        var owner = account(null);
        UUID id = create(owner).id();
        var config = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("review_upgrade").defaultSchema("review_upgrade");
        config.target("2").load().migrate();
        try {
            jdbc.update("insert into review_upgrade.users select * from public.users where id=?", owner.user().id());
            jdbc.update("insert into review_upgrade.incidents select * from public.incidents where id=?", id);
            jdbc.update("insert into review_upgrade.incident_reviews "
                    + "(id,incident_id,reviewed_by_user_id,decision,reason,reviewed_at) "
                    + "values(uuidv7(),?,?,'REQUEST_INFORMATION',null,now())", id, owner.user().id());
            var upgrade = org.flywaydb.core.Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas("review_upgrade").defaultSchema("review_upgrade").load();
            assertThatThrownBy(upgrade::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            assertThat(jdbc.queryForObject(
                    "select count(*) from review_upgrade.flyway_schema_history where success and version is not null",
                    Integer.class)).isEqualTo(2);
            jdbc.update("update review_upgrade.incident_reviews set reason='Información necesaria'");
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from review_upgrade.incident_reviews", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForList("select version from review_upgrade.flyway_schema_history "
                    + "where success and version is not null order by installed_rank", String.class))
                    .containsExactly("1", "2", "3", "4");
        } finally {
            jdbc.execute("drop schema review_upgrade cascade");
        }
    }

    @Test
    void reviewSqlEnforcesDecisionReasonsAndUnicodeBoundaries() {
        var owner = account(null);
        UUID id = create(owner).id();
        String sql = "insert into incident_reviews(id,incident_id,reviewed_by_user_id,decision,reason,reviewed_at) "
                + "values(uuidv7(),?,?,?,?,now())";
        for (String decision : List.of("VALIDATE", "REJECT", "REQUEST_INFORMATION")) {
            for (String reason : List.of("abc", "🚦".repeat(2000))) {
                assertThat(jdbc.update(sql, id, owner.user().id(), decision, reason)).isEqualTo(1);
            }
            for (String reason : List.of("ab", " ", "\u2003".repeat(3), " abc", "abc\t", "🚦".repeat(2001))) {
                assertThatThrownBy(() -> jdbc.update(sql, id, owner.user().id(), decision, reason))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
        assertThat(jdbc.update(sql, id, owner.user().id(), "VALIDATE", null)).isEqualTo(1);
        for (String decision : List.of("REJECT", "REQUEST_INFORMATION")) {
            assertThatThrownBy(() -> jdbc.update(sql, id, owner.user().id(), decision, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update(sql, id, null, "VALIDATE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transitionSqlForbidsInitialOrTerminalOriginsAndBoundsOptionalTexts() {
        var owner = account(null);
        UUID id = create(owner).id();
        String sql = "insert into status_changes(id,incident_id,from_status,to_status,reason,resolution_notes,changed_at) "
                + "values(uuidv7(),?,?,?,?,?,now())";
        assertThat(jdbc.update(sql, id, "RESOLVED", "WORK_ASSIGNED", "🚦".repeat(2000), "🚦".repeat(5000)))
                .isEqualTo(1);
        assertThat(jdbc.update(sql, id, "REPORTED", "VALIDATED", null, null)).isEqualTo(1);
        assertThat(jdbc.update(sql, id, "REPORTED", "VALIDATED", " ", "")).isEqualTo(1);
        for (String origin : new String[] { null, "CLOSED", "VALIDATED", "REOPENED" }) {
            assertThatThrownBy(() -> jdbc.update(sql, id, origin, "VALIDATED", null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        for (String target : new String[] { null, "REPORTED", "REOPENED" }) {
            assertThatThrownBy(() -> jdbc.update(sql, id, "REPORTED", target, null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update(sql, id, "REPORTED", "VALIDATED", "🚦".repeat(2001), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, id, "REPORTED", "VALIDATED", null, "🚦".repeat(5001)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void prioritySqlRequiresJustificationAndValidReferencesAndEnums() {
        var owner = account(null);
        UUID id = create(owner).id();
        String sql = "insert into priority_decisions(id,incident_id,decided_by_user_id,priority,source,justification,decided_at) "
                + "values(uuidv7(),?,?,?,?,?,now())";
        for (String reason : List.of("abc", "🚦".repeat(2000))) {
            assertThat(jdbc.update(sql, id, owner.user().id(), "HIGH", "MANUAL", reason)).isEqualTo(1);
        }
        for (String reason : new String[] { null, "", "ab", "   ", "\u2003".repeat(3), " abc", "abc\t",
                "🚦".repeat(2001) }) {
            assertThatThrownBy(() -> jdbc.update(sql, id, owner.user().id(), "HIGH", "ASSISTED", reason))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        for (Object[] values : List.of(new Object[] { id, null, "HIGH", "MANUAL", "abc" },
                new Object[] { id, owner.user().id(), "WRONG", "MANUAL", "abc" },
                new Object[] { id, owner.user().id(), "HIGH", "WRONG", "abc" },
                new Object[] { UUID.randomUUID(), owner.user().id(), "HIGH", "MANUAL", "abc" })) {
            assertThatThrownBy(() -> jdbc.update(sql, values)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void cleanupKeepsIncidentReferencesAndCorsSupportsConcurrencyHeaders() throws Exception {
        var owner = account(null);
        create(owner);
        jdbc.update("update users set email_verified=false,created_at=now()-interval '2 days' where id=?",
                owner.user().id());
        cleanup.clean();
        assertThat(count("users", "id", owner.user().id())).isEqualTo(1);
        mvc.perform(options(URL + "/" + UUID.randomUUID()).header("Origin", "http://localhost:7777")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "authorization,content-type,if-match"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("if-match")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("ETag")));
    }

    private TokenDTO account(String role) {
        var token = auth
                .register(new RegisterRequest("Usuario sintético", UUID.randomUUID() + "@example.org", "password-123"));
        jdbc.update("update users set email_verified=true where id=?", token.user().id());
        if (role != null) {
            jdbc.update("insert into user_roles values (?,?)", token.user().id(), role);
        }
        return token;
    }

    private String tag(TokenDTO actor, UUID id) {
        return IncidentETags.format(json, service.get(actor.user().id(), 0, id));
    }

    private IncidentDTO create(TokenDTO actor) {
        return service.create(actor.user().id(), 0, new CreateIncidentRequest("Bache grande", "Pavimento deteriorado",
                new CoordinatesDTO(36.7, -4.4, null), null));
    }

    private static UpdateIncidentRequest patchTitle(String title) {
        var request = new UpdateIncidentRequest();
        request.setTitle(title);
        return request;
    }

    private static String bearer(TokenDTO token) {
        return "Bearer " + token.accessToken();
    }

    private static UUID incidentId(String path) {
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    private long count(String table, String column, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + "=?", Long.class, id);
    }
}
