package es.grupo18.urbanpulse.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

/** Omitted fields preserve their value; explicit JSON null is rejected. */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PatchCategoryRequest {
    @CodePointLength(min = 2, max = 100)
    @Pattern(regexp = "(?s).*\\S.*")
    private String name;
    @CodePointLength(max = 500)
    private String description;
    private Boolean active;

    public PatchCategoryRequest() {
    }

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public PatchCategoryRequest(String name, String description, Boolean active) {
        this.name = name == null ? null : name.trim();
        this.description = description;
        this.active = active;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setName(String name) { this.name = name.trim(); }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setDescription(String description) { this.description = description; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setActive(Boolean active) { this.active = active; }

    public String name() { return name; }
    public String description() { return description; }
    public Boolean active() { return active; }

    @AssertTrue(message = "Debe proporcionar al menos un campo para actualizar")
    public boolean isNotEmpty() {
        return name != null || description != null || active != null;
    }
}
