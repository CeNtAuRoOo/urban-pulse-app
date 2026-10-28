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

import es.grupo18.urbanpulse.config.SecurityConfig;
import es.grupo18.urbanpulse.dto.DepartmentResponse;
import es.grupo18.urbanpulse.service.DepartmentService;
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
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class DepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authenticatedUserCanListDepartments() throws Exception {
        UUID id = UUID.fromString("0199f6de-0001-7000-8000-000000000001");
        when(departmentService.findAll()).thenReturn(List.of(
                new DepartmentResponse(id, "Mantenimiento Urbano", "MANT", "Conservación", true)));

        mockMvc.perform(get("/api/v1/departments").with(jwt()))
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
                        .with(jwt())
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
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(departmentService).patch(eq(id), any());
    }

    @Test
    void invalidRequestReturnsContractValidationStatus() throws Exception {
        mockMvc.perform(post("/api/v1/departments")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"code\":\"invalid code\"}"))
                .andExpect(status().isUnprocessableContent());
    }
}
