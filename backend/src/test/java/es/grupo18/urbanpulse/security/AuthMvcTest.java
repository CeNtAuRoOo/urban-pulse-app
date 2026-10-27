package es.grupo18.urbanpulse.security;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import es.grupo18.urbanpulse.config.SecurityConfig;
import es.grupo18.urbanpulse.controller.AuthController;
import es.grupo18.urbanpulse.dto.auth.*;
import es.grupo18.urbanpulse.dto.user.UserDTO;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.exception.*;
import es.grupo18.urbanpulse.repository.UserRepository;
import es.grupo18.urbanpulse.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, DatabaseJwtAuthenticationConverter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, ProblemDetailFactory.class, AuthMvcTest.ProbeController.class })
@TestPropertySource("classpath:auth-test.properties")
@ExtendWith(OutputCaptureExtension.class)
class AuthMvcTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JwtEncoder encoder;
    @MockitoBean
    AuthService auth;
    @MockitoBean
    UserRepository users;
    @MockitoBean
    InMemoryRateLimiter limiter;
    private final UUID id = UUID.randomUUID();
    private final String register = "{\"displayName\":\"Ciudadana\",\"email\":\"person@example.org\",\"password\":\"password-123\"}";
    private final String login = "{\"email\":\"person@example.org\",\"password\":\"password-123\"}";
    private final String change = "{\"currentPassword\":\"password-123\",\"newPassword\":\"password-456\"}";

    @BeforeEach
    void setup() {
        when(limiter.acquire(anyString(), any())).thenReturn(new InMemoryRateLimiter.Decision(true, 0));
        var dto = new TokenDTO("test-token", "Bearer", 7200,
                new UserDTO(id, "Ciudadana", "person@example.org", false, true, Instant.now(),
                        Set.of(RoleCode.CITIZEN)));
        when(auth.register(any())).thenReturn(dto);
        when(auth.login(any())).thenReturn(dto);
        state(RoleCode.CITIZEN, true, 0);
    }

    private void state(RoleCode role, boolean active, long version) {
        state(active, version, role);
    }

    private void state(boolean active, long version, RoleCode... roles) {
        var rows = Arrays.stream(roles).map(role -> {
            var row = mock(UserRepository.SecurityState.class);
            when(row.getActive()).thenReturn(active);
            when(row.getTokenVersion()).thenReturn(version);
            when(row.getRoleCode()).thenReturn(role);
            return row;
        }).toList();
        when(users.findSecurityState(id)).thenReturn(rows);
    }

    private String bearer() {
        Instant now = Instant.now();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("urbanpulse-tests").subject(id.toString()).issuedAt(now)
                        .expiresAt(now.plusSeconds(7200)).claim("ver", 0).claim("roles", List.of("ADMIN")).build()))
                .getTokenValue();
    }

    @Test
    void successResponsesAndNoSessionOrLocation() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Location"))
                .andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.user.roles[0]").value("CITIZEN"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.tokenVersion").doesNotExist())
                .andExpect(jsonPath("$.user.departmentMemberships").doesNotExist());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"));
        mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test
    void rejectsMalformedTypesUnknownFieldsAndValidationErrors() throws Exception {
        for (String body : List.of("{", "[]", "{\"displayName\":3}", register + " {}",
                register.replace("\"password-123\"", "true"))) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("INVALID_JSON"));
        }
        for (String field : List.of("roles", "active", "emailVerified", "tokenVersion", "extra")) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(register.substring(0, register.length() - 1) + ",\"" + field + "\":true}"))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors").isArray());
        }
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"a\",\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(change.replace("password-456", "x")))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(change.replace("}", ",\"active\":true}")))
                .andExpect(status().isUnprocessableContent());
        verifyNoInteractions(auth);
    }

    @Test
    void uniformBusinessAndUnexpectedErrors() throws Exception {
        when(auth.register(any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "EMAIL_CONFLICT", "El correo ya está registrado."));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_CONFLICT"));
        when(auth.login(any())).thenThrow(ApiException.unauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        doThrow(new IllegalStateException("SECRET SQL passwordHash")).when(auth).login(any());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.detail").value("Error interno."));
    }

    @Test
    void protectsEveryOtherRouteAndReadsCurrentDatabaseRoles() throws Exception {
        for (String path : List.of("/api/v1/users/me/password-changes", "/api/v1/unknown", "/api/v1/auth/refresh")) {
            mvc.perform(post(path)).andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get("/api/v1/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        String token = bearer();
        mvc.perform(get("/_test/admin").header("Authorization", token)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        state(true, 0, RoleCode.CITIZEN, RoleCode.ADMIN);
        mvc.perform(get("/_test/admin").header("Authorization", token)).andExpect(status().isOk());
        state(RoleCode.CITIZEN, true, 0);
        mvc.perform(get("/_test/admin").header("Authorization", token)).andExpect(status().isForbidden());
        state(false, 0, RoleCode.CITIZEN, RoleCode.ADMIN);
        mvc.perform(get("/_test/admin").header("Authorization", token)).andExpect(status().isUnauthorized());
        state(true, 1, RoleCode.CITIZEN, RoleCode.ADMIN);
        mvc.perform(get("/_test/admin").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimitPrecedesServiceAndUsesRemoteIpAndNormalizedEmail() throws Exception {
        when(limiter.acquire(eq("login-email:person@example.org"), any()))
                .thenReturn(new InMemoryRateLimiter.Decision(false, 12));
        mvc.perform(post("/api/v1/auth/login").header("X-Forwarded-For", "203.0.113.10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(login.replace("person@example.org", " PERSON@EXAMPLE.ORG ")))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "12"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT"));
        verify(limiter).acquire(eq("login-ip:127.0.0.1"), any());
        when(limiter.acquire(eq("register-ip:127.0.0.1"), any()))
                .thenReturn(new InMemoryRateLimiter.Decision(false, 4));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "4"));
        verifyNoInteractions(auth);
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin", "http://localhost:7777")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:7777"));
        mvc.perform(options("/api/v1/auth/login").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void databaseOutageDuringBearerAuthenticationIs503AndFailsClosed(CapturedOutput output) throws Exception {
        String sensitive = "jdbc:postgresql://private-db/users?password=PRIVATE_CREDENTIAL";
        when(users.findSecurityState(id)).thenThrow(new DataAccessResourceFailureException(sensitive));
        String token = bearer();
        mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(change))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Servicio temporalmente no disponible."))
                .andExpect(jsonPath("$.instance").value("/api/v1/users/me/password-changes"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(sensitive));
        verifyNoInteractions(auth, limiter);
        verify(users).findSecurityState(id);
        assertThat(output.getAll()).containsOnlyOnce("event=AUTH_BACKEND_UNAVAILABLE")
                .contains("DataAccessResourceFailureException").doesNotContain(sensitive, token, "password-123");
    }

    @Test
    void programmingFailureDuringBearerAuthenticationIs500(CapturedOutput output) throws Exception {
        for (RuntimeException failure : List.of(
                new BadSqlGrammarException("PRIVATE_TASK", "PRIVATE_SQL", new SQLException("PRIVATE_CAUSE", "42601")),
                new IllegalStateException("PRIVATE_CAUSE"))) {
            doThrow(failure).when(users).findSecurityState(id);
            mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(change))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().doesNotExist("WWW-Authenticate"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("Error interno."));
        }
        verifyNoInteractions(auth);
        assertThat(output.getAll()).contains("event=AUTH_BACKEND_ERROR")
                .doesNotContain("PRIVATE_TASK", "PRIVATE_SQL", "PRIVATE_CAUSE");
    }

    @Test
    void publicEndpointsDistinguishQueryAndTransactionOutagesFromSqlBugs(CapturedOutput output) throws Exception {
        for (RuntimeException failure : List.of(
                new DataAccessResourceFailureException("PRIVATE_CONNECTION"),
                new CannotCreateTransactionException("PRIVATE_TRANSACTION",
                        new SQLException("PRIVATE_CONNECTION", "08001")))) {
            doThrow(failure).when(auth).register(any());
            doThrow(failure).when(auth).login(any());
            for (String endpoint : List.of("register", "login")) {
                mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                        .content(endpoint.equals("register") ? register : login))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(header().doesNotExist("WWW-Authenticate"))
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                        .andExpect(jsonPath("$.detail").value("Servicio temporalmente no disponible."));
            }
        }
        var bug = new BadSqlGrammarException("PRIVATE_TASK", "PRIVATE_SQL", new SQLException("PRIVATE_CAUSE", "42601"));
        doThrow(bug).when(auth).register(any());
        doThrow(bug).when(auth).login(any());
        for (String endpoint : List.of("register", "login")) {
            mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON)
                    .content(endpoint.equals("register") ? register : login))
                    .andExpect(status().isInternalServerError())
                    .andExpect(header().doesNotExist("WWW-Authenticate"))
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("Error interno."));
        }
        assertThat(output.getAll()).contains("event=API_DATABASE_UNAVAILABLE", "event=API_INTERNAL_ERROR")
                .doesNotContain("PRIVATE_CONNECTION", "PRIVATE_TRANSACTION", "PRIVATE_TASK", "PRIVATE_SQL",
                        "PRIVATE_CAUSE");
    }

    @Test
    void activeAccountsWithoutCitizenCannotAuthenticate() throws Exception {
        for (RoleCode[] roles : List.of(new RoleCode[] { RoleCode.ADMIN }, new RoleCode[] { null })) {
            state(true, 0, roles);
            mvc.perform(post("/api/v1/users/me/password-changes").header("Authorization", bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(change))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.detail").value("Autenticación no válida."));
        }
        verifyNoInteractions(auth);
        verify(users, times(2)).findSecurityState(id);
    }

    @Test
    void bodyLimitAppliesToAllThreeEndpointsWithKnownOrUnknownLength() throws Exception {
        for (boolean knownLength : List.of(true, false)) {
            for (String path : List.of("/api/v1/auth/register", "/api/v1/auth/login",
                    "/api/v1/users/me/password-changes")) {
                String json = path.endsWith("register") ? register : path.endsWith("login") ? login : change;
                for (int size : List.of(16_384, 16_385)) {
                    String body = json + " ".repeat(size - json.getBytes(StandardCharsets.UTF_8).length);
                    var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
                    if (path.endsWith("password-changes")) {
                        request.header("Authorization", bearer());
                    }
                    if (knownLength) {
                        request.header("Content-Length", size);
                    } else {
                        request.with(original -> {
                            var unknown = spy(original);
                            doReturn(-1).when(unknown).getContentLength();
                            doReturn(-1L).when(unknown).getContentLengthLong();
                            unknown.removeHeader("Content-Length");
                            return unknown;
                        });
                    }
                    var result = mvc.perform(request);
                    if (size > 16_384) {
                        result.andExpect(status().isBadRequest())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
                    } else {
                        result.andExpect(
                                status().is(path.endsWith("register") ? 201 : path.endsWith("login") ? 200 : 204));
                    }
                }
            }
        }
        verify(auth, times(2)).changePassword(eq(id), eq(0L),
                eq(new ChangePasswordRequest("password-123", "password-456")));
        verify(auth, times(2)).register(any());
        verify(auth, times(2)).login(any());
        verify(limiter, times(4)).acquire(eq("register-ip:127.0.0.1"), any());
        verify(limiter, times(4)).acquire(eq("login-ip:127.0.0.1"), any());
        verify(limiter, times(2)).acquire(eq("login-email:person@example.org"), any());
        verifyNoMoreInteractions(limiter, auth);
    }

    @RestController
    static class ProbeController {
        @GetMapping("/_test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String admin() {
            return "ok";
        }
    }
}
