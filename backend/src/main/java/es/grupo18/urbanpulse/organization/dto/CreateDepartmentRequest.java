package es.grupo18.urbanpulse.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de un departamento; Bean Validation comprueba los valores tras recortar nombre y código.
 *
 * @param name nombre de 2 a 120 caracteres, recortado al crear
 * @param code código único de 2 a 20 caracteres ASCII
 * @param description descripción opcional de hasta 500 caracteres
 * @param active estado de la unidad; omitido o null equivale a true
 */
public record CreateDepartmentRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Size(min = 2, max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @Size(max = 500) String description,
        Boolean active) {
    /**
     * Recorta nombre y código antes de aplicar Bean Validation; conserva null para la validación obligatoria.
     */
    public CreateDepartmentRequest {
        name = name == null ? null : name.trim();
        code = code == null ? null : code.trim();
    }
}
