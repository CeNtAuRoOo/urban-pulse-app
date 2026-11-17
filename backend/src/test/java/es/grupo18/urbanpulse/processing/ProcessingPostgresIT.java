package es.grupo18.urbanpulse.processing;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import es.grupo18.urbanpulse.auth.dto.RegisterRequest;
import es.grupo18.urbanpulse.auth.dto.TokenDTO;
import es.grupo18.urbanpulse.auth.service.AuthService;
import es.grupo18.urbanpulse.incidents.api.IncidentETags;
import es.grupo18.urbanpulse.incidents.dto.CoordinatesDTO;
import es.grupo18.urbanpulse.incidents.dto.CreateIncidentRequest;
import es.grupo18.urbanpulse.incidents.dto.IncidentDTO;
import es.grupo18.urbanpulse.incidents.dto.UpdateIncidentRequest;
import es.grupo18.urbanpulse.incidents.model.IncidentPriority;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.incidents.service.IncidentService;
import es.grupo18.urbanpulse.processing.dto.InformationResponseRequest;
import es.grupo18.urbanpulse.processing.dto.PriorityRequest;
import es.grupo18.urbanpulse.processing.dto.ReviewRequest;
import es.grupo18.urbanpulse.processing.model.ReviewDecision;
import es.grupo18.urbanpulse.processing.service.ProcessingService;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.mail.EmailSender;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class ProcessingPostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName
                    .parse("postgis/postgis:18-3.6-alpine")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ProcessingService processing;
    @Autowired
    IncidentService incidents;
    @Autowired
    AuthService auth;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean
    EmailSender email;
    TokenDTO owner;
    TokenDTO operator;
    UUID category;
    UUID id;

    @BeforeEach
    void fixture() {
        jdbc.execute(
                "truncate duplicate_links, incident_information_responses, attachments, status_changes, incident_reviews, priority_decisions, incidents, audit_events");
        owner = account(null);
        operator = account("OPERATOR");
        category = UUID.randomUUID();
        jdbc.update("insert into categories(id,name,active) values(?,?,true)",
                category, "Categoría " + category);
        id = create(owner).id();
    }

    @Test
    void validationConfirmsReplacesCategoryAndCreatesExactlyOnePublicFact() throws Exception {
        var noReview = mvc.perform(get(path("review-outcome")).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latestReview").isEmpty()).andReturn();
        assertThat(noReview.getResponse().getContentAsString()).contains("\"latestReview\":null");
        UUID other = UUID.randomUUID();
        jdbc.update("insert into categories(id,name,active) values(?,?,true)", other, "Otra " + other);
        jdbc.update("update incidents set category_id=? where id=?", other, id);
        var result = mvc.perform(post(path("reviews")).header("Authorization", bearer(operator))
                .contentType(APPLICATION_JSON).content(validateBody(0)))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.resultingStatus").value("VALIDATED")).andReturn().getResponse();
        mvc.perform(get(result.getHeader("Location")).header("Authorization", bearer(operator)))
                .andExpect(status().isOk());
        var incident = current();
        assertThat(incident.category().id()).isEqualTo(category);
        assertThat(incident.priority()).isNull();
        assertThat(incident.version()).isEqualTo(1);
        assertThat(count("status_changes")).isEqualTo(1);
        var publicResult = mvc.perform(get(path("review-outcome")).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.latestReview.responsible").value("Ayuntamiento"))
                .andReturn().getResponse().getContentAsString();
        assertThat(publicResult).doesNotContain(operator.user().id().toString(), "reviewedBy");
        var history = incidents.history(owner.user().id(), 0, id, 1, 20);
        assertThat(history.data()).hasSize(2);
        assertThat(history.data().getLast().actor()).isNull();
        assertThat(history.data().getLast().eventType()).isEqualTo("REVIEWED");
        assertStatus(() -> processing.review(operator.user().id(), 0, id, review("REJECT", "Rechazo", 1)), 409);
    }

    @Test
    void variantsCategoryAndVersionsAreStrictAndAtomic() throws Exception {
        for (String body : List.of(
                "{\"decision\":\"VALIDATE\",\"expectedIncidentVersion\":0}",
                validateBody(0).replace("}", ",\"reason\":null}"),
                validateBody(0).replace("}", ",\"reason\":\"texto\"}"),
                "{\"decision\":\"REJECT\",\"reason\":\"motivo\",\"categoryId\":null,\"expectedIncidentVersion\":0}",
                "{\"decision\":\"REQUEST_INFORMATION\",\"reason\":\"  \",\"expectedIncidentVersion\":0}",
                validateBody(0).replace(category.toString(), UUID.randomUUID().toString()),
                validateBody(0).replace(",\"expectedIncidentVersion\":0", ""),
                validateBody(-1))) {
            mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isUnprocessableContent());
        }
        jdbc.update("update categories set active=false where id=?", category);
        mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                .content(validateBody(0))).andExpect(status().isUnprocessableContent());
        jdbc.update("update categories set active=true where id=?", category);
        mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                .content(validateBody(99))).andExpect(status().isConflict());
        mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                .content(validateBody(0).replace(category.toString(), "invalid"))).andExpect(status().isBadRequest());
        assertThat(count("incident_reviews")).isZero();
        assertThat(current().version()).isZero();
        assertThat(count("status_changes")).isZero();
    }

    @Test
    void onlyOriginalReporterRespondsAndMultipleRequestsRemainIndependent() throws Exception {
        var first = processing.review(operator.user().id(), 0, id,
                review("REQUEST_INFORMATION", "  Aclare ubicación  ", 0));
        assertThat(first.reason()).isEqualTo("Aclare ubicación");
        var awaiting = current();
        assertStatus(() -> processing.review(operator.user().id(), 0, id, validate(1)), 409);
        assertStatus(() -> processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Otra", 1)),
                409);
        assertStatus(
                () -> processing.respond(operator.user().id(), 0, id, new InformationResponseRequest("Respuesta", 1L)),
                403);
        var patch = new UpdateIncidentRequest();
        patch.setTitle("Título cambiado");
        assertStatus(() -> incidents.patch(owner.user().id(), 0, id, IncidentETags.format(json, awaiting), patch), 409);
        mvc.perform(
                post(path("information-responses")).header("Authorization", bearer(owner)).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"  Al lado del parque  \",\"expectedIncidentVersion\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reviewId").value(first.id().toString()))
                .andDo(r -> mvc
                        .perform(get(r.getResponse().getHeader("Location")).header("Authorization", bearer(owner)))
                        .andExpect(status().isOk()));
        assertThat(current().title()).isEqualTo(awaiting.title());
        assertThat(current().description()).isEqualTo(awaiting.description());
        assertThat(current().status()).isEqualTo(IncidentStatus.REPORTED);
        assertStatus(() -> processing.respond(owner.user().id(), 0, id,
                new InformationResponseRequest("Otra respuesta", 2L)), 409);
        incidents.patch(owner.user().id(), 0, id, IncidentETags.format(json, current()), patch);
        var second = processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Aclare detalle", 3));
        assertThat(second.id()).isNotEqualTo(first.id());
        processing.respond(owner.user().id(), 0, id, new InformationResponseRequest("Detalle concreto", 4L));
        assertThat(processing.responses(owner.user().id(), 0, id, 1, 1).pagination().totalItems()).isEqualTo(2);
        assertThat(processing.reviews(operator.user().id(), 0, id, 1, 1).data().getFirst().id()).isEqualTo(second.id());
        assertThat(processing.outcome(owner.user().id(), 0, id).informationPending()).isFalse();
        assertThat(processing.outcome(owner.user().id(), 0, id).latestReview().resultingStatus())
                .isEqualTo(IncidentStatus.AWAITING_INFORMATION);
        assertThat(count("status_changes")).isEqualTo(4);
    }

    @Test
    void waitingCanBeRejectedWithoutFabricatingResponse() {
        processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Aclare", 0));
        processing.review(operator.user().id(), 0, id, review("REJECT", "Sin respuesta suficiente", 1));
        assertThat(count("incident_information_responses")).isZero();
        assertThat(current().status()).isEqualTo(IncidentStatus.REJECTED);
        assertStatus(() -> processing.respond(owner.user().id(), 0, id, new InformationResponseRequest("Tardía", 2L)),
                409);
        assertThat(count("status_changes")).isEqualTo(2);
    }

    @Test
    void permissionsRevalidateDatabaseAndNeverAllowSelfReviewEvenAdmin() throws Exception {
        var stranger = account(null);
        var admin = account("ADMIN");
        for (TokenDTO staff : List.of(operator, admin)) {
            UUID own = create(staff).id();
            assertStatus(() -> processing.review(staff.user().id(), 0, own, validate(0)), 403);
            jdbc.update("update incidents set status='AWAITING_INFORMATION' where id=?", own);
            assertStatus(() -> processing.review(staff.user().id(), 0, own, review("REJECT", "Motivo", 0)), 403);
        }
        for (String route : List.of("reviews", "priority-decisions", "information-responses", "review-outcome")) {
            mvc.perform(get(path(route)).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
            mvc.perform(get(path(route))).andExpect(status().isUnauthorized());
        }
        mvc.perform(get(path("reviews")).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(get(path("priority-decisions")).header("Authorization", bearer(owner)))
                .andExpect(status().isForbidden());
        assertStatus(() -> processing.review(owner.user().id(), 0, id, validate(0)), 403);
        jdbc.update("update users set active=false where id=?", operator.user().id());
        assertStatus(() -> processing.review(operator.user().id(), 0, id, validate(0)), 401);
        jdbc.update("update users set active=true,email_verified=false where id=?", operator.user().id());
        assertStatus(() -> processing.review(operator.user().id(), 0, id, validate(0)), 403);
        jdbc.update("update users set email_verified=true,token_version=1 where id=?", operator.user().id());
        mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                .content(validateBody(0))).andExpect(status().isUnauthorized());
        assertStatus(() -> processing.review(operator.user().id(), 0, id, validate(0)), 401);
        processing.review(admin.user().id(), 0, id, validate(0));
    }

    @Test
    void nestedResourcesNeverCrossParentsAndPaginationRejectsUnknownParameters() throws Exception {
        var review = processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Datos", 0));
        var response = processing.respond(owner.user().id(), 0, id, new InformationResponseRequest("Respuesta", 1L));
        processing.review(operator.user().id(), 0, id, validate(2));
        var priority = processing.prioritize(operator.user().id(), 0, id,
                new PriorityRequest(IncidentPriority.HIGH, "Riesgo", 3L));
        UUID other = create(owner).id();
        for (String route : List.of("reviews/" + review.id(), "information-responses/" + response.id(),
                "priority-decisions/" + priority.id())) {
            mvc.perform(get("/api/v1/incidents/" + other + "/" + route).header("Authorization", bearer(operator)))
                    .andExpect(status().isNotFound());
        }
        for (String route : List.of("reviews", "information-responses", "priority-decisions")) {
            for (String query : List.of("?page=0", "?pageSize=101", "?userId=" + owner.user().id())) {
                mvc.perform(get(path(route) + query).header("Authorization", bearer(operator)))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    @Test
    void manualPriorityChangesOnlyBeforeDerivationAndNeverLeaksJustification() throws Exception {
        assertStatus(() -> processing.prioritize(operator.user().id(), 0, id, priority(0)), 409);
        processing.review(operator.user().id(), 0, id, validate(0));
        String before = IncidentETags.format(json, current());
        var result = mvc.perform(post(path("priority-decisions")).header("Authorization", bearer(operator))
                .contentType(APPLICATION_JSON)
                .content("{\"priority\":\"HIGH\",\"justification\":\"Informe privado\",\"expectedIncidentVersion\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.source").value("MANUAL")).andReturn()
                .getResponse();
        mvc.perform(get(result.getHeader("Location")).header("Authorization", bearer(operator)))
                .andExpect(status().isOk());
        assertThat(IncidentETags.format(json, current())).isNotEqualTo(before);
        assertStatus(() -> processing.prioritize(operator.user().id(), 0, id, priority(2)), 409);
        assertThat(count("priority_decisions")).isEqualTo(1);
        String history = json.writeValueAsString(incidents.history(owner.user().id(), 0, id, 1, 20));
        assertThat(history).doesNotContain("Informe privado", operator.user().id().toString(), "MANUAL");
        for (String source : List.of("MANUAL", "ASSISTED")) {
            mvc.perform(post(path("priority-decisions")).header("Authorization", bearer(operator))
                    .contentType(APPLICATION_JSON)
                    .content("{\"priority\":\"LOW\",\"source\":\"" + source
                            + "\",\"justification\":\"abc\",\"expectedIncidentVersion\":2}"))
                    .andExpect(status().isUnprocessableContent());
        }
        processing.prioritize(operator.user().id(), 0, id,
                new PriorityRequest(IncidentPriority.CRITICAL, "Riesgo mayor", 2L));
        for (IncidentStatus state : IncidentStatus.values()) {
            if (state == IncidentStatus.VALIDATED)
                continue;
            jdbc.update("update incidents set status=? where id=?", state.name(), id);
            assertStatus(() -> processing.prioritize(operator.user().id(), 0, id, priority(3)), 409);
        }
        assertThat(count("priority_decisions")).isEqualTo(2);
    }

    @Test
    void priorityOrderingNullPlacementStableTiesAndTotalsAreSqlBased() throws Exception {
        for (IncidentPriority level : IncidentPriority.values()) {
            for (int i = 0; i < 2; i++) {
                var incident = create(owner);
                jdbc.update(
                        "update incidents set priority=?,status='VALIDATED',created_at='2026-01-01',updated_at=now() where id=?",
                        level.name(), incident.id());
            }
        }
        var desc = incidents.list(operator.user().id(), 0, 1, 100, null, null, null, null, null, "priority,desc");
        assertThat(desc.data()).extracting(incident -> incident.priority()).containsExactly(
                IncidentPriority.CRITICAL, IncidentPriority.CRITICAL, IncidentPriority.HIGH, IncidentPriority.HIGH,
                IncidentPriority.MEDIUM, IncidentPriority.MEDIUM, IncidentPriority.LOW, IncidentPriority.LOW, null);
        var asc = incidents.list(operator.user().id(), 0, 1, 100, null, null, null, null, null, "priority,asc");
        assertThat(asc.data().getFirst().priority()).isEqualTo(IncidentPriority.LOW);
        assertThat(asc.data().getLast().priority()).isNull();
        for (int i = 0; i < 8; i += 2) {
            assertThat(desc.data().get(i).id().toString()).isGreaterThan(desc.data().get(i + 1).id().toString());
        }
        var page = incidents.list(operator.user().id(), 0, 2, 1, null, null, null, null, IncidentPriority.HIGH,
                "priority,desc");
        assertThat(page.pagination().totalItems()).isEqualTo(2);
        assertThat(page.data()).hasSize(1);
        for (String query : List.of("?sort=title,asc", "?priority=WRONG", "?other=x")) {
            mvc.perform(get("/api/v1/incidents" + query).header("Authorization", bearer(operator)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void unicodeBoundariesAndBodyLimitsApplyToAllThreePosts() throws Exception {
        for (String reason : new String[] { null, "ab", " ", "\u2003".repeat(3), "🚦".repeat(2001), "a\u0000b" }) {
            assertStatus(() -> processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", reason, 0)),
                    422);
        }
        processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", " 🚦".repeat(1000) + " ", 0));
        for (String message : new String[] { null, "ab", " ", "🚦".repeat(2001) }) {
            assertStatus(
                    () -> processing.respond(owner.user().id(), 0, id, new InformationResponseRequest(message, 1L)),
                    422);
        }
        processing.respond(owner.user().id(), 0, id, new InformationResponseRequest("🚦".repeat(2000), 1L));
        for (String route : List.of("reviews", "information-responses", "priority-decisions")) {
            mvc.perform(post(path(route)).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                    .content(" ".repeat(65537))).andExpect(status().isContentTooLarge());
        }
    }

    @Test
    void concurrentReviewsPrioritiesResponsesAndRejectionHaveSingleWinner() throws Exception {
        var second = account("ADMIN");
        race(() -> processing.review(operator.user().id(), 0, id, validate(0)),
                () -> processing.review(second.user().id(), 0, id, validate(0)));
        assertThat(count("incident_reviews")).isEqualTo(1);
        race(() -> processing.prioritize(operator.user().id(), 0, id, priority(1)),
                () -> processing.prioritize(second.user().id(), 0, id,
                        new PriorityRequest(IncidentPriority.LOW, "Motivo", 1L)));
        assertThat(count("priority_decisions")).isEqualTo(1);
        id = create(owner).id();
        processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Datos", 0));
        race(() -> processing.respond(owner.user().id(), 0, id, new InformationResponseRequest("Respuesta uno", 1L)),
                () -> processing.respond(owner.user().id(), 0, id,
                        new InformationResponseRequest("Respuesta dos", 1L)));
        assertThat(count("incident_information_responses")).isEqualTo(1);
        processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Más datos", 2));
        race(() -> processing.respond(owner.user().id(), 0, id, new InformationResponseRequest("Nueva respuesta", 3L)),
                () -> processing.review(second.user().id(), 0, id, review("REJECT", "No procede", 3)));
        assertThat(current().version()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from status_changes where incident_id=?", Integer.class, id))
                .isEqualTo(4);
    }

    @Test
    void auditFailureRollsBackAggregateAndAllDependentRecords() {
        jdbc.execute(
                "alter table audit_events add constraint fail_processing check(action not in ('INCIDENT_REVIEWED','INCIDENT_PRIORITY_CHANGED','INCIDENT_INFORMATION_RESPONDED')) not valid");
        try {
            assertThatThrownBy(() -> processing.review(operator.user().id(), 0, id, validate(0)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(current().version()).isZero();
            assertThat(current().category()).isNull();
            assertThat(count("incident_reviews")).isZero();
            assertThat(count("status_changes")).isZero();
        } finally {
            jdbc.execute("alter table audit_events drop constraint fail_processing");
        }
        processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Datos", 0));
        jdbc.execute(
                "alter table audit_events add constraint fail_processing check(action <> 'INCIDENT_INFORMATION_RESPONDED') not valid");
        try {
            assertThatThrownBy(
                    () -> processing.respond(owner.user().id(), 0, id, new InformationResponseRequest("Respuesta", 1L)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(count("incident_information_responses")).isZero();
            assertThat(current().version()).isEqualTo(1);
        } finally {
            jdbc.execute("alter table audit_events drop constraint fail_processing");
        }
    }

    @Test
    void strictJsonRejectsFractionalVersionsDuplicateKeysAndUnknownIdentity() throws Exception {
        mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                .content(validateBody(0).replace("\"VALIDATE\"", "0"))).andExpect(status().isBadRequest());
        for (String value : List.of("0.5", "\"0\"", "true", "9223372036854775808")) {
            mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                    .content(validateBody(0).replace("\"expectedIncidentVersion\":0",
                            "\"expectedIncidentVersion\":" + value)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                .content(validateBody(0).replace("}", ",\"decision\":\"REJECT\"}"))).andExpect(status().isBadRequest());
        mvc.perform(
                post(path("information-responses")).header("Authorization", bearer(owner)).contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Respuesta\",\"expectedIncidentVersion\":0,\"author\":\""
                                + owner.user().id() + "\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void validationExplicitlyConfirmsAnExistingActiveCategoryAndPriorityRequiresText() throws Exception {
        jdbc.update("update incidents set category_id=? where id=?", category, id);
        processing.review(operator.user().id(), 0, id, validate(0));
        assertThat(current().category().id()).isEqualTo(category);
        assertThat(current().priority()).isNull();
        for (String text : new String[] { null, "ab", " ", "🚦".repeat(2001) }) {
            assertStatus(() -> processing.prioritize(operator.user().id(), 0, id,
                    new PriorityRequest(IncidentPriority.HIGH, text, 1L)), 422);
        }
        assertStatus(() -> processing.prioritize(operator.user().id(), 0, id,
                new PriorityRequest(null, "Motivo", 1L)), 422);
        processing.prioritize(operator.user().id(), 0, id,
                new PriorityRequest(IncidentPriority.LOW, "🚦".repeat(2000), 1L));
        assertThat(processing.priorities(operator.user().id(), 0, id, 1, 20).data().getFirst().justification())
                .isEqualTo("🚦".repeat(2000));
    }

    @Test
    void departmentalMembershipNeverGrantsGlobalProcessingPermissions() throws Exception {
        for (String role : List.of("TECHNICIAN", "DEPARTMENT_COORDINATOR")) {
            var user = account(null);
            UUID department = UUID.randomUUID();
            jdbc.update("insert into departments(id,name,code) values(?,?,?)", department, "Dept " + department,
                    "D" + department.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
            jdbc.update(
                    "insert into department_memberships(id,user_id,department_id,role_code,joined_at) values(uuidv7(),?,?,?,now())",
                    user.user().id(), department, role);
            assertStatus(() -> processing.review(user.user().id(), 0, id, validate(0)), 404);
            assertStatus(() -> processing.reviews(user.user().id(), 0, id, 1, 20), 404);
            UUID own = create(user).id();
            assertStatus(() -> processing.review(user.user().id(), 0, own, validate(0)), 403);
            mvc.perform(get("/api/v1/incidents/" + own + "/priority-decisions").header("Authorization", bearer(user)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void assignedDepartmentBlocksPriorityEvenIfStatusStillValidated() {
        processing.review(operator.user().id(), 0, id, validate(0));
        UUID department = UUID.randomUUID();
        jdbc.update("insert into departments(id,name,code) values(?,?,?)", department, "Dept " + department,
                "D" + department.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
        jdbc.update("update incidents set assigned_department_id=? where id=?", department, id);
        assertStatus(() -> processing.prioritize(operator.user().id(), 0, id, priority(1)), 409);
        assertThat(count("priority_decisions")).isZero();
    }

    @Test
    void priorityAndDeferredCommitFailuresNeverReturnCreatedOrLeaveRows() throws Exception {
        processing.review(operator.user().id(), 0, id, validate(0));
        jdbc.execute(
                "alter table audit_events add constraint fail_priority check(action <> 'INCIDENT_PRIORITY_CHANGED') not valid");
        try {
            assertThatThrownBy(() -> processing.prioritize(operator.user().id(), 0, id, priority(1)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(current().priority()).isNull();
            assertThat(current().version()).isEqualTo(1);
            assertThat(count("priority_decisions")).isZero();
        } finally {
            jdbc.execute("alter table audit_events drop constraint fail_priority");
        }
        id = create(owner).id();
        jdbc.execute(
                "create function fail_review_commit() returns trigger language plpgsql as $$ begin raise exception 'synthetic failure'; end $$");
        jdbc.execute(
                "create constraint trigger fail_review_commit after insert on incident_reviews deferrable initially deferred for each row execute function fail_review_commit()");
        try {
            mvc.perform(post(path("reviews")).header("Authorization", bearer(operator)).contentType(APPLICATION_JSON)
                    .content(validateBody(0))).andExpect(status().isInternalServerError());
            assertThat(current().version()).isZero();
            assertThat(
                    jdbc.queryForObject("select count(*) from incident_reviews where incident_id=?", Integer.class, id))
                    .isZero();
            assertThat(
                    jdbc.queryForObject("select count(*) from status_changes where incident_id=?", Integer.class, id))
                    .isZero();
        } finally {
            jdbc.execute("drop trigger fail_review_commit on incident_reviews");
            jdbc.execute("drop function fail_review_commit()");
        }
    }

    @Test
    void categoryDeactivationLockPreventsValidationAgainstAnInactiveSnapshot() throws Exception {
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deactivate = executor
                    .submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                            .executeWithoutResult(tx -> {
                                jdbc.queryForObject("select id from categories where id=? for update", UUID.class,
                                        category);
                                locked.countDown();
                                try {
                                    if (!release.await(10, TimeUnit.SECONDS))
                                        throw new AssertionError("timeout");
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(e);
                                }
                                jdbc.update("update categories set active=false where id=?", category);
                            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var validate = executor.submit(() -> {
                try {
                    processing.review(operator.user().id(), 0, id, validate(0));
                    return 201;
                } catch (ApiException e) {
                    return e.status().value();
                }
            });
            release.countDown();
            deactivate.get(20, TimeUnit.SECONDS);
            assertThat(validate.get(20, TimeUnit.SECONDS)).isEqualTo(422);
        } finally {
            release.countDown();
        }
        assertThat(current().status()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(count("incident_reviews")).isZero();
    }

    @Test
    void historyQueriesFetchToOneAssociationsWithoutNPlusOne() {
        for (int i = 0; i < 12; i++) {
            processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Datos " + i, i * 2));
            processing.respond(owner.user().id(), 0, id,
                    new InformationResponseRequest("Respuesta " + i, (long) i * 2 + 1));
        }
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            var page = processing.reviews(operator.user().id(), 0, id, 1, 10);
            assertThat(page.data()).hasSize(10);
            assertThat(page.pagination().totalItems()).isEqualTo(12);
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
            statistics.clear();
            assertThat(processing.responses(owner.user().id(), 0, id, 1, 10).data()).hasSize(10);
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    /**
     * Los motivos públicos sobreviven al saneamiento; las identidades y
     * justificaciones no.
     */
    @Test
    void publicHistoryKeepsReasonsButNeverMunicipalIdentityOrPriorityJustification() throws Exception {
        String employeeName = "Revisor sintético privado";
        jdbc.update("update users set display_name=? where id=?", employeeName, operator.user().id());
        processing.review(operator.user().id(), 0, id, review("REQUEST_INFORMATION", "Aclare el punto exacto", 0));
        processing.respond(owner.user().id(), 0, id, new InformationResponseRequest("Entrada norte del parque", 1L));
        processing.review(operator.user().id(), 0, id, review("REJECT", "Fuera del ámbito municipal", 2));
        var rejected = mvc.perform(get(path("history")).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[1].details.reason").value("Aclare el punto exacto"))
                .andExpect(jsonPath("$.data[2].actor.id").value(owner.user().id().toString()))
                .andExpect(jsonPath("$.data[3].details.reason").value("Fuera del ámbito municipal"))
                .andReturn().getResponse().getContentAsString();
        assertThat(rejected).doesNotContain(operator.user().id().toString(), employeeName);
        assertThat(json.readTree(rejected).get("data").get(1).has("actor")).isFalse();
        assertThat(json.readTree(rejected).get("data").get(3).has("actor")).isFalse();
        id = create(owner).id();
        processing.review(operator.user().id(), 0, id, validate(0));
        processing.prioritize(operator.user().id(), 0, id,
                new PriorityRequest(IncidentPriority.HIGH, "Justificación solo interna", 1L));
        var prioritized = mvc.perform(get(path("history")).header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[2].eventType").value("PRIORITY_CHANGED"))
                .andExpect(jsonPath("$.data[2].details.priority").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        assertThat(prioritized).doesNotContain(operator.user().id().toString(), employeeName,
                "Justificación solo interna");
        assertThat(json.readTree(prioritized).get("data").get(2).has("actor")).isFalse();
        mvc.perform(get(path("priority-decisions")).header("Authorization", bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].decidedBy.id").value(operator.user().id().toString()))
                .andExpect(jsonPath("$.data[0].justification").value("Justificación solo interna"));
    }

    private void race(Callable<?> first, Callable<?> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(task -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    task.call();
                    return 201;
                } catch (ApiException e) {
                    return e.status().value();
                }
            })).toList();
            assertThat(List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    private ReviewRequest validate(long version) {
        var r = review("VALIDATE", null, version);
        r.setCategoryId(category);
        return r;
    }

    private ReviewRequest review(String decision, String reason, long version) {
        var r = new ReviewRequest();
        r.setDecision(ReviewDecision.valueOf(decision));
        r.setExpectedIncidentVersion(version);
        if (reason != null)
            r.setReason(reason);
        return r;
    }

    private PriorityRequest priority(long version) {
        return new PriorityRequest(IncidentPriority.HIGH, "Riesgo importante", version);
    }

    private TokenDTO account(String role) {
        var t = auth
                .register(new RegisterRequest("Usuario sintético", UUID.randomUUID() + "@example.org", "password-123"));
        jdbc.update("update users set email_verified=true where id=?", t.user().id());
        if (role != null)
            jdbc.update("insert into user_roles values (?,?)", t.user().id(), role);
        return t;
    }

    private IncidentDTO create(TokenDTO t) {
        return incidents.create(t.user().id(), 0, new CreateIncidentRequest("Bache grande", "Pavimento deteriorado",
                new CoordinatesDTO(36.7, -4.4, null), null));
    }

    private IncidentDTO current() {
        return incidents.get(owner.user().id(), 0, id);
    }

    private String path(String suffix) {
        return "/api/v1/incidents/" + id + "/" + suffix;
    }

    private String validateBody(long version) {
        return "{\"decision\":\"VALIDATE\",\"categoryId\":\"" + category + "\",\"expectedIncidentVersion\":" + version
                + "}";
    }

    private String bearer(TokenDTO t) {
        return "Bearer " + t.accessToken();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static void assertStatus(Runnable task, int status) {
        assertThatThrownBy(task::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status().value()).isEqualTo(status));
    }
}
