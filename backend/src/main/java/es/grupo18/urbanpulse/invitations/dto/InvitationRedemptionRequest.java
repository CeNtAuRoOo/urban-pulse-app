package es.grupo18.urbanpulse.invitations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Código presentado por el titular; ningún atributo del permiso procede del
 * cliente.
 *
 * @param invitationCode Base64URL canónico de 256 bits, sin padding
 */
public record InvitationRedemptionRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]") String invitationCode) {

    /** @return representación segura para logs y diagnósticos */
    @Override
    public String toString() {
        return "InvitationRedemptionRequest[REDACTED]";
    }
}
