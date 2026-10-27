package es.grupo18.urbanpulse.entity.embeddable;

import java.io.Serializable;
import java.util.UUID;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import jakarta.persistence.*;
import lombok.*;

/**
 * Representa la clave primaria compuesta de la entidad UserRole.
 * Identifica de forma única la asignación de un rol global a un usuario
 * mediante su identificador y el código del rol.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserRoleId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 32)
    private RoleCode roleCode;
}
