package es.grupo18.urbanpulse.organization.dto;

import java.util.UUID;

/**
 * Proyección pública del catálogo; no expone las fechas internas ni las
 * relaciones JPA.
 *
 * @param id          identidad UUIDv7 del departamento
 * @param name        nombre de 2 a 120 caracteres, recortado al crear
 * @param code        código único de 2 a 20 caracteres ASCII
 * @param description descripción opcional de hasta 500 caracteres
 * @param active      estado de la unidad
 */
public record DepartmentResponse(
        UUID id,
        String name,
        String code,
        String description,
        boolean active) {
}
