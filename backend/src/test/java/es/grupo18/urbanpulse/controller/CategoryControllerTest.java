package es.grupo18.urbanpulse.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import es.grupo18.urbanpulse.dto.CategoryResponse;
import es.grupo18.urbanpulse.security.DatabaseJwtAuthenticationConverter;
import es.grupo18.urbanpulse.service.CategoryService;
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

@WebMvcTest(CategoryController.class)
@Import({
        ApiExceptionHandler.class,
        SecurityConfig.class,
        DatabaseJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ProblemDetailFactory.class
})
@TestPropertySource("classpath:auth-test.properties")
class CategoryControllerTest {

    @Test
    void responsesIncludeExplicitNullDescriptionAsRequiredByOpenApi() throws Exception {
        var response = new CategoryResponse(ID, "Tráfico", null, true);
        when(categoryService.findAll()).thenReturn(List.of(response));
        when(categoryService.findById(ID)).thenReturn(response);
        when(categoryService.create(any())).thenReturn(response);
        when(categoryService.patch(eq(ID), any())).thenReturn(response);

        mockMvc.perform(get(URL).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").hasJsonPath())
                .andExpect(jsonPath("$[0].description").value(nullValue()));
        mockMvc.perform(get(URL + "/" + ID).with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").hasJsonPath())
                .andExpect(jsonPath("$.description").value(nullValue()));
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tráfico\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").hasJsonPath())
                .andExpect(jsonPath("$.description").value(nullValue()));
        mockMvc.perform(patch(URL + "/" + ID).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").hasJsonPath())
                .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    void verifiedCitizenCanReadInactiveDetail() throws Exception {
        when(categoryService.findById(ID)).thenReturn(new CategoryResponse(ID, "Tráfico", null, false));
        mockMvc.perform(get(URL + "/" + ID).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CITIZEN"), new SimpleGrantedAuthority("EMAIL_VERIFIED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.name").value("Tráfico"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void createNormalizesBeforeValidationAndAllowsDocumentedNulls() throws Exception {
        when(categoryService.create(any())).thenReturn(new CategoryResponse(ID, "ab", null, true));
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  ab  \",\"description\":null,\"active\":null}"))
                .andExpect(status().isCreated());
        verify(categoryService).create(new es.grupo18.urbanpulse.dto.CreateCategoryRequest("ab", null, null));
    }

    @Test
    void rejectsInvalidCreateValuesWithoutCallingService() throws Exception {
        for (String body : List.of("{}", "{\"name\":null}", "{\"name\":\"  \"}",
                "{\"name\":\"" + "x".repeat(101) + "\"}", "{\"name\":\"Valid\",\"roles\":[\"ADMIN\"]}")) {
            mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        for (String body : List.of("{", "{\"name\":3}", "{\"name\":\"Valid\",\"active\":\"false\"}",
                "{\"name\":\"First\",\"name\":\"Second\"}", "{\"name\":\"Valid\"} {}")) {
            mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        org.mockito.Mockito.verifyNoInteractions(categoryService);
    }

    @Test
    void everyOperationRequiresAuthenticationAndBothWritesRequireAdmin() throws Exception {
        for (var request : List.of(get(URL), get(URL + "/" + ID), post(URL), patch(URL + "/" + ID))) {
            mockMvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"));
        }
        for (var request : List.of(post(URL), patch(URL + "/" + ID))) {
            mockMvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CITIZEN"),
                    new SimpleGrantedAuthority("EMAIL_VERIFIED"))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        org.mockito.Mockito.verifyNoInteractions(categoryService);
    }

    private static final String URL = "/api/v1/categories";
    private static final UUID ID = UUID.fromString("0199f6de-0001-7000-8000-000000000001");

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("EMAIL_VERIFIED"), new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void internalAndUnavailableFailuresDoNotLeakDatabaseDetails() throws Exception {
        when(categoryService.findAll()).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private SQL"));
        mockMvc.perform(get(URL).with(admin())).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Error interno."));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("credentials"))
                .when(categoryService).findAll();
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
        for (String body : List.of("{", "{\"active\":null}", "{\"name\":null,\"active\":false}", "{\"description\":null}", "{\"active\":\"false\"}")) {
            mockMvc.perform(patch(URL + "/" + ID).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        }
        mockMvc.perform(get(URL + "/invalid").with(admin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void returnsUniformDomainProblems() throws Exception {
        when(categoryService.findById(ID)).thenThrow(new es.grupo18.urbanpulse.service.CategoryNotFoundException());
        mockMvc.perform(get(URL + "/" + ID).with(admin())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND")).andExpect(jsonPath("$.instance").value(URL + "/" + ID));
        when(categoryService.create(any())).thenThrow(new es.grupo18.urbanpulse.service.DuplicateCategoryException());
        mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Duplicado\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_DUPLICATE"));
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
    private CategoryService categoryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private InMemoryRateLimiter limiter;

    @Test
    void authenticatedUserCanListCategories() throws Exception {
        UUID id = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        when(categoryService.findAll()).thenReturn(List.of(
                new CategoryResponse(id, "Mantenimiento Urbano", "Conservación", true)));

        mockMvc.perform(get("/api/v1/categories")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Mantenimiento Urbano"));
    }

    @Test
    void unauthenticatedWriteIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Movilidad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminWriteIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Movilidad\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateCategoryAndReceivesLocation() throws Exception {
        UUID id = UUID.fromString("0199f6de-0004-7000-8000-000000000004");
        when(categoryService.create(any())).thenReturn(
                new CategoryResponse(id, "Movilidad", null, true));

        mockMvc.perform(post("/api/v1/categories")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED),
                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Movilidad\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/categories/" + id))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void patchRequiresAdminAndDelegatesToService() throws Exception {
        UUID id = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        when(categoryService.patch(eq(id), any())).thenReturn(
                new CategoryResponse(id, "Limpieza", null, false));

        mockMvc.perform(patch("/api/v1/categories/{categoryId}", id)
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED),
                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(categoryService).patch(eq(id), any());
    }

    @Test
    void invalidRequestReturnsContractValidationStatus() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority(
                                DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED),
                        new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}"))
                .andExpect(status().isUnprocessableContent());
    }
}
