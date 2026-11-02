package es.grupo18.urbanpulse.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.CodePointLength;

public record CreateCategoryRequest(
        @NotBlank @CodePointLength(min = 2, max = 100) String name,
        @CodePointLength(max = 500) String description,
        Boolean active) {
    public CreateCategoryRequest {
        name = name == null ? null : name.trim();
    }
}
