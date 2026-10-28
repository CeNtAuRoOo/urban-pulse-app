package es.grupo18.urbanpulse.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatchDepartmentRequest(
        @Size(min = 2, max = 120) @Pattern(regexp = ".*\\S.*") String name,
        @Size(min = 2, max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @Size(max = 500) String description,
        Boolean active) {

    @AssertTrue(message = "Debe proporcionar al menos un campo para actualizar")
    public boolean hasAtLeastOneField() {
        return name != null || code != null || description != null || active != null;
    }
}
