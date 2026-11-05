package es.grupo18.urbanpulse.invitations.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import es.grupo18.urbanpulse.identity.model.RoleCode;

/**
 * Dos variantes disjuntas: el rol selecciona los únicos campos de entrada
 * admitidos.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "role", visible = true, defaultImpl = GlobalInvitationRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = GlobalInvitationRequest.class, names = { "OPERATOR", "ADMIN" }),
        @JsonSubTypes.Type(value = DepartmentInvitationRequest.class, names = { "TECHNICIAN",
                "DEPARTMENT_COORDINATOR" })
})
public sealed interface InvitationRequest permits GlobalInvitationRequest, DepartmentInvitationRequest {

    /** @return correo normalizado del destinatario */
    String email();

    /** @return rol municipal solicitado */
    RoleCode role();

    /** @return departamento exclusivamente en la variante departamental */
    UUID departmentId();
}
