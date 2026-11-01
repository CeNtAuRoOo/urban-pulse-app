package es.grupo18.urbanpulse.dto;

import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        String name,
        String code,
        String description,
        boolean active) {
}
