package es.grupo18.urbanpulse.categories.integration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.auth.dto.RegisterRequest;
import es.grupo18.urbanpulse.auth.service.AuthService;
import es.grupo18.urbanpulse.categories.dto.CategoryResponse;
import es.grupo18.urbanpulse.categories.dto.CreateCategoryRequest;
import es.grupo18.urbanpulse.categories.dto.PatchCategoryRequest;
import es.grupo18.urbanpulse.categories.service.CategoryService;
import es.grupo18.urbanpulse.categories.service.DuplicateCategoryException;
import es.grupo18.urbanpulse.shared.mail.EmailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class CategoryPostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    CategoryService service;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService auth;
    @Autowired
    PlatformTransactionManager transactions;
    @MockitoBean
    EmailSender emailSender;

    @org.junit.jupiter.api.BeforeAll
    static void initialSchemaContainsAllModulesWithoutCatalogSeeds(@Autowired JdbcTemplate jdbc) {
        assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema='public'",
                String.class))
                .containsExactlyInAnyOrder("flyway_schema_history", "users", "roles", "user_roles",
                        "email_verifications", "departments", "department_memberships", "staff_invitations",
                        "audit_events", "categories", "incidents", "status_changes", "incident_reviews",
                        "priority_decisions");
        assertThat(jdbc.queryForObject("select count(*) from categories", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from departments", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select code from roles", String.class))
                .containsExactlyInAnyOrder("CITIZEN", "ADMIN", "OPERATOR", "DEPARTMENT_COORDINATOR", "TECHNICIAN");
    }

    @Test
    void flywayAndHibernateRoundTripLifecycleAndStableOrdering() {
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                String.class))
                .containsExactly("1", "2", "3");
        var created = service.create(new CreateCategoryRequest(" \tTráfico  urbano \n", null, null));
        assertThat(created.id().version()).isEqualTo(7);
        assertThat(created.id().variant()).isEqualTo(2);
        assertThat(created.name()).isEqualTo("Tráfico  urbano");
        assertThat(created.active()).isTrue();
        assertThat(service.findById(created.id())).isEqualTo(created);
        service.patch(created.id(), new PatchCategoryRequest(null, "Description", false));
        assertThat(service.findById(created.id()).active()).isFalse();
        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("tráfico  URBANO", null, true)))
                .isInstanceOf(DuplicateCategoryException.class);
        var reactivated = service.patch(created.id(), new PatchCategoryRequest(null, "", true));
        assertThat(reactivated.id()).isEqualTo(created.id());
        assertThat(reactivated.active()).isTrue();
        assertThat(reactivated.description()).isEmpty();
        assertThat(service.findAll().stream()
                .map(category -> category.id())
                .toList())
                .containsExactlyElementsOf(
                        jdbc.queryForList(
                                "select id from categories order by name, id",
                                UUID.class));
    }

    @Test
    void postgresEnforcesNullsBoundsNormalizationAndUniqueness() {
        UUID id = jdbc.queryForObject("insert into categories(name) values ('SQL category') returning id", UUID.class);
        assertThat(id.version()).isEqualTo(7);
        assertThat(service.findById(id).active()).isTrue();
        assertThat(service.findById(id).description()).isNull();
        for (String sql : List.of(
                "insert into categories(name) values ('sql CATEGORY')",
                "insert into categories(name) values (null)",
                "insert into categories(name) values ('')",
                "insert into categories(name) values (' ')",
                "insert into categories(name) values ('x')",
                "insert into categories(name) values ('🚦')",
                "insert into categories(name) values (U&'\\2003 \\2003')",
                "insert into categories(name) values (repeat('x',101))",
                "insert into categories(name) values (' SQL category ')",
                "insert into categories(name) values (E'\\tSQL category\\n')",
                "insert into categories(name,active) values ('SQL invalid',null)",
                "insert into categories(name,description) values ('SQL invalid',repeat('x',501))")) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update("insert into categories(name,description,active) values (?, ?, false)", "x".repeat(100),
                "x".repeat(500));
        var unicode = service.create(new CreateCategoryRequest("🚦".repeat(100), "🚦".repeat(500), true));
        assertThat(service.findById(unicode.id())).isEqualTo(unicode);
        jdbc.update("update categories set active=false where id=?", id);
        assertThatThrownBy(() -> jdbc.update("insert into categories(name) values ('sql category')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void realJwtUsesEffectiveRolesVerificationAndRevocation() throws Exception {
        var token = auth.register(new RegisterRequest("Administradora", "categories@example.org", "password-123"));
        String bearer = "Bearer " + token.accessToken();
        String url = "/api/v1/categories";
        mvc.perform(get(url).header("Authorization", bearer)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        jdbc.update("update users set email_verified=true where id=?", token.user().id());
        mvc.perform(get(url).header("Authorization", bearer)).andExpect(status().isOk());
        String body = "{\"name\":\"HTTP category\"}";
        mvc.perform(post(url).header("Authorization", bearer).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        jdbc.update("insert into user_roles values (?, 'ADMIN')", token.user().id());
        var result = mvc.perform(post(url).header("Authorization", bearer).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true)).andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        mvc.perform(patch(location).header("Authorization", bearer).contentType(APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get(location).header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post(url).header("Authorization", bearer).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_DUPLICATE"));
        jdbc.update("delete from user_roles where user_id=? and role_code='ADMIN'", token.user().id());
        mvc.perform(patch(location).header("Authorization", bearer).contentType(APPLICATION_JSON)
                .content("{\"active\":true}"))
                .andExpect(status().isForbidden());
        jdbc.update("update users set token_version=token_version+1 where id=?", token.user().id());
        mvc.perform(get(url).header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", "Bearer invalid.jwt.signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentCreatesHaveOneWinner() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> create = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    service.create(new CreateCategoryRequest("Concurrent category", null, true));
                    return 201;
                } catch (DuplicateCategoryException ex) {
                    return 409;
                }
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test
    void concurrentRenamesHaveOneWinnerAndRollbackLoser() throws Exception {
        var one = service.create(new CreateCategoryRequest("Rename one", "Original", true));
        var two = service.create(new CreateCategoryRequest("Rename two", "Original", true));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            List<Future<Integer>> results = List.of(one, two).stream().map(category -> executor.submit(() -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    service.patch(category.id(), new PatchCategoryRequest("Same target", "Changed", false));
                    return 200;
                } catch (DuplicateCategoryException ex) {
                    return 409;
                }
            })).toList();
            start.countDown();
            assertThat(List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            var loser = List.of(service.findById(one.id()), service.findById(two.id())).stream()
                    .filter(category -> !category.name().equals("Same target")).findFirst().orElseThrow();
            assertThat(loser.active()).isTrue();
            assertThat(loser.description()).isEqualTo("Original");
        }
    }

    @Test
    void rowLockMakesPartialUpdateWaitAndPreserveCommittedFields() throws Exception {
        var created = service.create(new CreateCategoryRequest("Parallel category", "Before", true));
        var firstPid = new CompletableFuture<Integer>();
        var release = new CountDownLatch(1);
        var secondPid = new CompletableFuture<Integer>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                service.patch(created.id(), new PatchCategoryRequest("Renamed category", null, null));
                firstPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                try {
                    if (!release.await(30, TimeUnit.SECONDS))
                        throw new IllegalStateException("Lock test timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return true;
            }));
            Future<CategoryResponse> second;
            try {
                int blocker = firstPid.get(10, TimeUnit.SECONDS);
                second = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                    secondPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    return service.patch(created.id(), new PatchCategoryRequest(null, null, false));
                }));
                int waiter = secondPid.get(10, TimeUnit.SECONDS);
                assertThat(waiter).isNotEqualTo(blocker);

                // Observe the actual PostgreSQL wait, not merely an unfinished Java task.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean blocked;
                do {
                    blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                            "select ? = any(pg_blocking_pids(?))", Boolean.class, blocker, waiter));
                    if (blocked || second.isDone())
                        break;
                    Thread.sleep(20);
                } while (System.nanoTime() < deadline);
                assertThat(blocked).as("Second transaction must wait for the first transaction in PostgreSQL").isTrue();
                assertThat(second.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            var result = service.findById(created.id());
            assertThat(result.name()).isEqualTo("Renamed category");
            assertThat(result.active()).isFalse();
            assertThat(result.description()).isEqualTo("Before");
        }
    }
}
