package es.grupo18.urbanpulse.organization.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import es.grupo18.urbanpulse.organization.dto.CreateDepartmentRequest;
import es.grupo18.urbanpulse.organization.dto.DepartmentResponse;
import es.grupo18.urbanpulse.organization.dto.PatchDepartmentRequest;
import es.grupo18.urbanpulse.organization.service.DepartmentService;

/** Operaciones REST del catálogo municipal, con DTO independientes de JPA. */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * Configura el adaptador HTTP del catálogo.
     *
     * @param departmentService servicio transaccional compartido
     */
    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * Lista unidades activas e inactivas ordenadas por nombre; requiere correo verificado.
     *
     * @return catálogo sin paginación ni fechas internas
     */
    @GetMapping
    public List<DepartmentResponse> findAll() {
        return departmentService.findAll();
    }

    /**
     * Consulta una unidad conservando la visibilidad de departamentos inactivos.
     *
     * @param departmentId UUID de la unidad
     * @return proyección pública del departamento
     * @throws es.grupo18.urbanpulse.organization.service.DepartmentNotFoundException si no existe la unidad
     */
    @GetMapping("/{departmentId}")
    public DepartmentResponse findById(@PathVariable UUID departmentId) {
        return departmentService.findById(departmentId);
    }

    /**
     * Da de alta una unidad; la cadena de seguridad exige correo verificado y ADMIN.
     *
     * @param request datos validados de alta
     * @return HTTP 201 con la proyección creada y cabecera Location
     */
    @PostMapping
    public ResponseEntity<DepartmentResponse> create(
            @Valid @RequestBody CreateDepartmentRequest request) {
        DepartmentResponse response = departmentService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{departmentId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(location)
                .body(response);
    }

    /**
     * Modifica los campos presentes sin eliminar la unidad; exige ADMIN verificado.
     *
     * @param departmentId UUID de la unidad
     * @param request campos validados; omitir conserva, null explícito no se admite
     * @return proyección resultante de la actualización
     */
    @PatchMapping("/{departmentId}")
    public DepartmentResponse patch(
            @PathVariable UUID departmentId,
            @Valid @RequestBody PatchDepartmentRequest request) {
        return departmentService.patch(departmentId, request);
    }
}
