package es.grupo18.urbanpulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDepartmentRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Size(min = 2, max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @Size(max = 500) String description,
        Boolean active) {
    public CreateDepartmentRequest {
        name = name == null ? null : name.trim();
        code = code == null ? null : code.trim();
    }
}
