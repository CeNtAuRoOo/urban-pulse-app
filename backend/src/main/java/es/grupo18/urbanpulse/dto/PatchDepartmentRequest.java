package es.grupo18.urbanpulse.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Setters distinguish omitted properties from explicit JSON null. */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PatchDepartmentRequest {
    @Size(min = 2, max = 120)
    @Pattern(regexp = "(?s).*\\S.*")
    private String name;
    @Size(min = 2, max = 20)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String code;
    @Size(max = 500)
    private String description;
    private Boolean active;

    public PatchDepartmentRequest() {
    }

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public PatchDepartmentRequest(String name, String code, String description, Boolean active) {
        this.name = name == null ? null : name.trim();
        this.code = code == null ? null : code.trim();
        this.description = description;
        this.active = active;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setName(String name) { this.name = name.trim(); }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setCode(String code) { this.code = code.trim(); }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setDescription(String description) { this.description = description; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setActive(Boolean active) { this.active = active; }

    public String name() { return name; }
    public String code() { return code; }
    public String description() { return description; }
    public Boolean active() { return active; }

    @AssertTrue(message = "Debe proporcionar al menos un campo para actualizar")
    public boolean isNotEmpty() {
        return name != null || code != null || description != null || active != null;
    }
}
