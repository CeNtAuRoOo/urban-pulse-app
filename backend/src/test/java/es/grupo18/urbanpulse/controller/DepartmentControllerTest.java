package es.grupo18.urbanpulse.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import es.grupo18.urbanpulse.exception.ProblemDetailFactory;
import es.grupo18.urbanpulse.config.SecurityConfig;
import es.grupo18.urbanpulse.dto.DepartmentResponse;
import es.grupo18.urbanpulse.security.DatabaseJwtAuthenticationConverter;
import es.grupo18.urbanpulse.service.DepartmentService;
import es.grupo18.urbanpulse.repository.UserRepository;
import es.grupo18.urbanpulse.security.InMemoryRateLimiter;
import es.grupo18.urbanpulse.security.RestAccessDeniedHandler;
import es.grupo18.urbanpulse.security.RestAuthenticationEntryPoint;
import org.springframework.test.context.TestPropertySource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DepartmentController.class)
@Import({
        ApiExceptionHandler.class,
        SecurityConfig.class,
        DatabaseJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ProblemDetailFactory.class
})
@TestPropertySource("classpath:auth-test.properties")
class DepartmentControllerTest {

    private static final String URL = "/api/v1/departments";
    private static final UUID ID = UUID.fromString("0199f6de-0001-7000-8000-000000000001");

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("EMAIL_VERIFIED"), new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void internalAndUnavailableFailuresDoNotLeakDatabaseDetails() throws Exception {
        when(departmentService.findAll()).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private SQL"));
        mockMvc.perform(get(URL).with(admin())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Error interno."));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("credentials"))
                .when(departmentService).findAll();
        mockMvc.perform(get(URL).with(admin())).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void rejectsEmptyPatchAndUnknownFields() throws Exception {
        for (String body : List.of("{}", "{\"other\":true}", "{\"notEmpty\":true,\"active\":true}", "{\"name\":\" \"}", "{\"code\":\"!\"}", "{\"description\":\"" + "x".repeat(501) + "\"}")) {
            mockMvc.perform(patch(URL + "/" + ID).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").exists());
        }
    }

    @Test
    void rejectsExplicitNullAndMalformedJson() throws Exception {
        for (String body : List.of("{", "{\"active\":null}", "{\"name\":null,\"active\":false}", "{\"code\":null}", "{\"description\":null}", "{\"active\":\"false\"}")) {
            mockMvc.perform(patch(URL + "/" + ID).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        }
        mockMvc.perform(get(URL + "/invalid").with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void returnsUniformDomainProblems() throws Exception {
        when(departmentService.findById(ID)).thenThrow(new es.grupo18.urbanpulse.service.DepartmentNotFoundException(ID));
        mockMvc.perform(get(URL + "/" + ID).with(admin())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND")).andExpect(jsonPath("$.instance").value(URL + "/" + ID));
        when(departmentService.create(any())).thenThrow(new es.grupo18.urbanpulse.service.DuplicateDepartmentException("nombre", "Duplicado"));
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Duplicado\",\"code\":\"DUP\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEPARTMENT_DUPLICATE"));
    }

    @Test
    void allOperationsRejectUnverifiedEvenAdmin() throws Exception {
        for (var request : List.of(get(URL), get(URL + "/" + ID), post(URL), patch(URL + "/" + ID))) {
            mockMvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(patch(URL + "/" + ID).with(jwt().authorities(new SimpleGrantedAuthority("EMAIL_VERIFIED"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private InMemoryRateLimiter limiter;

    @Test
    void authenticatedUserCanListDepartments() throws Exception {
        UUID id = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        when(departmentService.findAll()).thenReturn(List.of(
                new DepartmentResponse(id, "Mantenimiento Urbano", "MANT", "Conservación", true)));

        mockMvc.perform(get("/api/v1/departments")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("MANT"));
    }

    @Test
    void unauthenticatedWriteIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Movilidad\",\"code\":\"MOV\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminWriteIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/departments")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Movilidad\",\"code\":\"MOV\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateDepartmentAndReceivesLocation() throws Exception {
        UUID id = UUID.fromString("0199f6de-0004-7000-8000-000000000004");
        when(departmentService.create(any())).thenReturn(
                new DepartmentResponse(id, "Movilidad", "MOV", null, true));

        mockMvc.perform(post("/api/v1/departments")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED),
                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Movilidad\",\"code\":\"MOV\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/departments/" + id))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void patchRequiresAdminAndDelegatesToService() throws Exception {
        UUID id = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        when(departmentService.patch(eq(id), any())).thenReturn(
                new DepartmentResponse(id, "Limpieza", "LIMP", null, false));

        mockMvc.perform(patch("/api/v1/departments/{departmentId}", id)
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED),
                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(departmentService).patch(eq(id), any());
    }

    @Test
    void invalidRequestReturnsContractValidationStatus() throws Exception {
        mockMvc.perform(post("/api/v1/departments")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED),
                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"code\":\"invalid code\"}"))
                .andExpect(status().isUnprocessableContent());
    }
}
