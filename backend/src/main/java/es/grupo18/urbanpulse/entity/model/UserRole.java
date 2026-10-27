package es.grupo18.urbanpulse.entity.model;

import es.grupo18.urbanpulse.entity.embeddable.UserRoleId;
import jakarta.persistence.*;
import lombok.*;

/**
 * Representa la asignación de un rol global a un usuario.
 * Establece la relación entre User y Role mediante una clave primaria
 * compuesta y rechaza la asignación de roles departamentales.
 */
@Entity
@Table(name = "user_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole {

    @EmbeddedId
    private UserRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("roleCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_code", nullable = false)
    private Role role;

    public UserRole(User user, Role role) {
        if (!role.getCode().isGlobal()) {
            throw new IllegalArgumentException("UserRole requires a global role");
        }

        this.user = user;
        this.role = role;
        this.id = new UserRoleId(user.getId(), role.getCode());
    }
}
