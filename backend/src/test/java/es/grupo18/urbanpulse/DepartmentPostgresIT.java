package es.grupo18.urbanpulse;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import es.grupo18.urbanpulse.service.mail.EmailSender;
import es.grupo18.urbanpulse.dto.auth.RegisterRequest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import org.springframework.dao.DataIntegrityViolationException;
import es.grupo18.urbanpulse.dto.CreateDepartmentRequest;
import es.grupo18.urbanpulse.dto.DepartmentResponse;
import es.grupo18.urbanpulse.dto.PatchDepartmentRequest;
import es.grupo18.urbanpulse.repository.DepartmentRepository;
import es.grupo18.urbanpulse.service.AuthService;
import es.grupo18.urbanpulse.service.DepartmentService;
import es.grupo18.urbanpulse.service.DuplicateDepartmentException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class DepartmentPostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DepartmentService service;
    @Autowired DepartmentRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @MockitoBean
    EmailSender emailSender;

    @Test
    void realJwtUsesCurrentDatabaseRolesAndVerification() throws Exception {
        var token = auth.register(new RegisterRequest(
                "Administradora", "departments@example.org", "password-123"));
        String bearer = "Bearer " + token.accessToken();
        String url = "/api/v1/departments";
        mvc.perform(get(url).header("Authorization", bearer))
                .andExpect(status().isForbidden());
        jdbc.update("update users set email_verified=true where id=?", token.user().id());
        mvc.perform(get(url).header("Authorization", bearer))
                .andExpect(status().isOk());
        String body = "{\"name\":\"HTTP unit\",\"code\":\"HTTP\"}";
        mvc.perform(post(url).header("Authorization", bearer)
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        jdbc.update("insert into user_roles values (?, 'ADMIN')", token.user().id());
        var result = mvc.perform(post(url).header("Authorization", bearer)
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String location = result.getResponse().getHeader("Location");
        mvc.perform(patch(location).header("Authorization", bearer)
                .contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        jdbc.update("delete from user_roles where user_id=? and role_code='ADMIN'", token.user().id());
        mvc.perform(patch(location).header("Authorization", bearer)
                .contentType(APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void migratesSeedsAndRoundTripsLifecycle() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success and version in ('1','2')", Integer.class)).isEqualTo(2);
        assertThat(service.findById(UUID.fromString("0199f6de-0001-7000-8000-000000000001")).code()).isEqualTo("MANT");
        var created = service.create(new CreateDepartmentRequest("  Movilidad  ", " mov ", "Original", null));
        assertThat(created.id().version()).isEqualTo(7);
        assertThat(created.id().variant()).isEqualTo(2);
        assertThat(created.name()).isEqualTo("Movilidad");
        assertThat(created.code()).isEqualTo("MOV");
        var before = repository.findById(created.id()).orElseThrow();
        assertThat(before.getCreatedAt()).isEqualTo(before.getUpdatedAt());
        service.patch(created.id(), new PatchDepartmentRequest(null, null, "", false));
        var after = repository.findById(created.id()).orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isAfter(before.getUpdatedAt());
        assertThat(after.isActive()).isFalse();
        assertThat(after.getDescription()).isEmpty();
        assertThat(after.getName()).isEqualTo("Movilidad");
        assertThat(service.findAll()).extracting(DepartmentResponse::id).contains(created.id());
        assertThatThrownBy(() -> service.create(new CreateDepartmentRequest("movilidad", "OTHER", null, true)))
                .isInstanceOf(DuplicateDepartmentException.class);
        assertThatThrownBy(() -> service.create(new CreateDepartmentRequest("Otra unidad", "mov", null, true)))
                .isInstanceOf(DuplicateDepartmentException.class);
    }

    @Test
    void databaseRejectsDuplicatesAndInvalidValuesAndGeneratesDefaultUuid() {
        UUID id = jdbc.queryForObject("insert into departments(name,code) values ('SQL unit','SQL') returning id", UUID.class);
        assertThat(id.version()).isEqualTo(7);
        for (String sql : List.of(
                "insert into departments(name,code) values ('sql UNIT','UNIQUE')",
                "insert into departments(name,code) values ('Different','SQL')",
                "insert into departments(name,code) values (' ','EMPTY')",
                "insert into departments(name,code) values ('Valid','bad code')",
                "insert into departments(name,code) values ('Valid','lower')",
                "insert into departments(name,code,description) values ('Valid','DESC',repeat('x',501))")) {
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void concurrentCreatesHaveOneWinner() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> create = () -> {
                start.await();
                try {
                    service.create(new CreateDepartmentRequest("Concurrent", "RACE", null, true));
                    return 201;
                } catch (DuplicateDepartmentException ex) {
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
    void concurrentPartialUpdatesPreserveBothChanges() throws Exception {
        var created = service.create(new CreateDepartmentRequest("Parallel", "PARALLEL", "Before", true));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return service.patch(created.id(), new PatchDepartmentRequest("Renamed", null, null, null));
            });
            var second = executor.submit(() -> {
                start.await();
                return service.patch(created.id(), new PatchDepartmentRequest(null, null, null, false));
            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            var result = service.findById(created.id());
            assertThat(result.name()).isEqualTo("Renamed");
            assertThat(result.active()).isFalse();
            assertThat(result.description()).isEqualTo("Before");
        }
    }
}
