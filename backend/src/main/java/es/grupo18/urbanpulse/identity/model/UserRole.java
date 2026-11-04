package es.grupo18.urbanpulse.identity.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    /**
     * Crea una asignación de rol global para un usuario.
     * Construye la clave primaria compuesta utilizando el identificador
     * del usuario y el código del rol.
     *
     * @param user usuario al que se asignará el rol
     * @param role rol global que se desea asignar
     * @throws IllegalArgumentException si el rol tiene ámbito departamental
     */
    public UserRole(User user, Role role) {
        if (!role.getCode().isGlobal()) {
            throw new IllegalArgumentException("UserRole requires a global role");
        }

        this.user = user;
        this.role = role;
        this.id = new UserRoleId(user.getId(), role.getCode());
    }
}
