
package es.grupo18.urbanpulse.identity.api;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.identity.dto.AdminUpdateUserRequest;
import es.grupo18.urbanpulse.identity.dto.UpdateMyProfileRequest;
import es.grupo18.urbanpulse.identity.dto.UserDTO;
import es.grupo18.urbanpulse.identity.dto.UsersPageDTO;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.identity.service.UserService;
import es.grupo18.urbanpulse.shared.error.ProblemDetailFactory;
import es.grupo18.urbanpulse.shared.pagination.PageMetadata;
import es.grupo18.urbanpulse.shared.ratelimit.InMemoryRateLimiter;
import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;
import es.grupo18.urbanpulse.shared.security.RestAccessDeniedHandler;
import es.grupo18.urbanpulse.shared.security.RestAuthenticationEntryPoint;
import es.grupo18.urbanpulse.shared.security.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        DatabaseJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ProblemDetailFactory.class
})
@TestPropertySource("classpath:auth-test.properties")
class UserControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtEncoder encoder;

    @MockitoBean
    UserService userService;

    @MockitoBean
    UserRepository users;

    @MockitoBean
    InMemoryRateLimiter limiter;

    private final UUID id = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    private UserDTO citizen;
    private UserDTO admin;

    @BeforeEach
    void setup() {
        when(limiter.acquire(anyString(), any()))
                .thenReturn(new InMemoryRateLimiter.Decision(true, 0));

        citizen = new UserDTO(
                id,
                "Ciudadana",
                "person@example.org",
                true,
                true,
                Instant.now(),
                Set.of(RoleCode.CITIZEN),
                List.of());

        admin = new UserDTO(
                otherId,
                "Administrador",
                "admin@example.org",
                true,
                true,
                Instant.now(),
                Set.of(RoleCode.CITIZEN, RoleCode.ADMIN),
                List.of());

        state(RoleCode.CITIZEN);
    }

    private void state(RoleCode... roles) {
        var rows = Arrays.stream(roles).map(role -> {
            var row = mock(UserRepository.SecurityState.class);

            when(row.getActive()).thenReturn(true);
            when(row.getEmailVerified()).thenReturn(true);
            when(row.getTokenVersion()).thenReturn(0L);
            when(row.getRoleCode()).thenReturn(role);

            return row;
        }).toList();

        when(users.findSecurityState(id)).thenReturn(rows);
    }

    private String bearer() {
        Instant now = Instant.now();

        return "Bearer " + encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .issuer("urbanpulse-tests")
                                .subject(id.toString())
                                .issuedAt(now)
                                .expiresAt(now.plusSeconds(7200))
                                .claim("ver", 0)
                                .claim("roles", List.of("ADMIN"))
                                .build()))
                .getTokenValue();
    }

    @Test
    void getMeReturnsAuthenticatedUser() throws Exception {
        when(userService.getMe(id)).thenReturn(citizen);

        mvc.perform(get("/api/v1/users/me")
                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.displayName").value("Ciudadana"))
                .andExpect(jsonPath("$.email").value("person@example.org"))
                .andExpect(jsonPath("$.roles[0]").value("CITIZEN"));

        verify(userService).getMe(id);
    }

    @Test
    void patchMeUpdatesAuthenticatedUser() throws Exception {
        UserDTO updated = new UserDTO(
                id,
                "Nuevo Nombre",
                "person@example.org",
                true,
                true,
                Instant.now(),
                Set.of(RoleCode.CITIZEN),
                List.of());

        when(userService.updateMe(
                eq(id),
                any(UpdateMyProfileRequest.class)))
                .thenReturn(updated);

        mvc.perform(patch("/api/v1/users/me")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "displayName": "Nuevo Nombre"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Nuevo Nombre"));

        verify(userService).updateMe(
                id,
                new UpdateMyProfileRequest("Nuevo Nombre"));
    }

    @Test
    void patchMeRejectsInvalidDisplayName() throws Exception {
        for (String name : List.of("", " ", "A", "N".repeat(121))) {
            mvc.perform(patch("/api/v1/users/me")
                    .header("Authorization", bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "displayName": "%s"
                            }
                            """.formatted(name)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(userService);
    }

    @Test
    void patchMeRejectsMalformedJson() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));

        verifyNoInteractions(userService);
    }

    @Test
    void citizenCannotAccessAdminEndpoints() throws Exception {
        mvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/users")
                .header("Authorization", bearer()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/users/" + otherId)
                .header("Authorization", bearer()))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/users/" + otherId)
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "active": false
                        }
                        """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void adminCanGetUserById() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);

        when(userService.getUserById(otherId)).thenReturn(admin);

        mvc.perform(get("/api/v1/users/" + otherId)
                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(otherId.toString()))
                .andExpect(jsonPath("$.displayName").value("Administrador"));

        verify(userService).getUserById(otherId);
    }

    @Test
    void adminCanUpdateUserStatus() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);

        UserDTO inactive = new UserDTO(
                otherId,
                "Administrador",
                "admin@example.org",
                true,
                false,
                Instant.now(),
                Set.of(RoleCode.CITIZEN, RoleCode.ADMIN),
                List.of());

        when(userService.updateUser(
                eq(otherId),
                any(AdminUpdateUserRequest.class)))
                .thenReturn(inactive);

        mvc.perform(patch("/api/v1/users/" + otherId)
                .header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "active": false
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(userService).updateUser(
                otherId,
                new AdminUpdateUserRequest(false));
    }

    @Test
    void adminCanListUsers() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);

        UsersPageDTO page = new UsersPageDTO(
                List.of(admin),
                new PageMetadata(1, 20, 1, 1));

        when(userService.getUsers(1, 20, null, null))
                .thenReturn(page);

        mvc.perform(get("/api/v1/users")
                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.pageSize").value(20))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));

        verify(userService).getUsers(1, 20, null, null);
    }

    @Test
    void adminCanFilterUsers() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);

        UsersPageDTO page = new UsersPageDTO(
                List.of(admin),
                new PageMetadata(2, 10, 11, 2));

        when(userService.getUsers(2, 10, RoleCode.ADMIN, true))
                .thenReturn(page);

        mvc.perform(get("/api/v1/users")
                .param("page", "2")
                .param("pageSize", "10")
                .param("role", "ADMIN")
                .param("active", "true")
                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page").value(2))
                .andExpect(jsonPath("$.pagination.pageSize").value(10));

        verify(userService).getUsers(2, 10, RoleCode.ADMIN, true);
    }

    @Test
    void adminListRejectsInvalidPagination() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);

        for (String[] params : List.of(
                new String[] { "0", "20" },
                new String[] { "1", "0" },
                new String[] { "1", "101" })) {

            mvc.perform(get("/api/v1/users")
                    .param("page", params[0])
                    .param("pageSize", params[1])
                    .header("Authorization", bearer()))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(userService);
    }

    @Test
    void adminListAcceptsDepartmentRoleFilter() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);

        UsersPageDTO page = new UsersPageDTO(
                List.of(),
                new PageMetadata(1, 20, 0, 0));
        when(userService.getUsers(1, 20, RoleCode.TECHNICIAN, null))
                .thenReturn(page);

        mvc.perform(get("/api/v1/users")
                .param("role", "TECHNICIAN")
                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(userService).getUsers(1, 20, RoleCode.TECHNICIAN, null);
    }

    @Test
    void rejectsMalformedUserId() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);

        mvc.perform(get("/api/v1/users/not-a-uuid")
                .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void protectedEndpointsRejectMissingOrInvalidToken() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/users/me")
                .header("Authorization", "Bearer invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "displayName": "Nuevo Nombre"
                        }
                        """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void validatesNormalizedProfileAndRejectsEveryPrivilegedField() throws Exception {
        for (String body : List.of("{}", "{\"displayName\":null}", "{\"displayName\":\" A \"}",
                "{\"displayName\":\"\u00a0\u00a0\"}", "{\"displayName\":\"A\\tB\"}")) {
            mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String field : List.of("email", "roles", "active", "emailVerified", "tokenVersion",
                "departmentMemberships")) {
            mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"displayName\":\"Valid\",\"" + field + "\":true}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("UNKNOWN_PROPERTY"));
        }
        verifyNoInteractions(userService);
        when(userService.updateMe(eq(id), any())).thenReturn(citizen);
        mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"  " + "😀".repeat(120) + "  \"}"))
                .andExpect(status().isOk());
        verify(userService).updateMe(eq(id), org.mockito.ArgumentMatchers
                .argThat(request -> request.displayName().codePointCount(0, request.displayName().length()) == 120));
    }

    @Test
    void unverifiedAdministratorCannotUseAnyUserOperation() throws Exception {
        var rows = Arrays.stream(new RoleCode[] { RoleCode.CITIZEN, RoleCode.ADMIN }).map(role -> {
            var row = mock(UserRepository.SecurityState.class);
            when(row.getActive()).thenReturn(true);
            when(row.getEmailVerified()).thenReturn(false);
            when(row.getTokenVersion()).thenReturn(0L);
            when(row.getRoleCode()).thenReturn(role);
            return row;
        }).toList();
        when(users.findSecurityState(id)).thenReturn(rows);
        for (String path : List.of("/api/v1/users/me", "/api/v1/users", "/api/v1/users/" + otherId)) {
            mvc.perform(get(path).header("Authorization", bearer())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        }
        for (String path : List.of("/api/v1/users/me", "/api/v1/users/" + otherId))
            mvc.perform(patch(path).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"active\":false}")).andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }

    @Test
    void returnsLastAdministratorConflictAndNeverCachesUserDetails() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);
        when(userService.updateUser(eq(otherId), any())).thenThrow(new es.grupo18.urbanpulse.shared.error.ApiException(
                org.springframework.http.HttpStatus.CONFLICT, "LAST_ACTIVE_ADMIN",
                "Debe permanecer un administrador."));
        mvc.perform(patch("/api/v1/users/" + otherId).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));
        when(userService.getUserById(otherId)).thenReturn(admin);
        mvc.perform(get("/api/v1/users/" + otherId).header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokenVersion").doesNotExist());
    }

    @Test
    void rejectsOffsetOverflowBeforeCallingService() throws Exception {
        state(RoleCode.CITIZEN, RoleCode.ADMIN);
        mvc.perform(get("/api/v1/users").param("page", "2147483647").param("pageSize", "100")
                .header("Authorization", bearer())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HTTP_ERROR"));
        verifyNoInteractions(userService);
    }
}
