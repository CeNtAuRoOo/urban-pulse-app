package es.grupo18.urbanpulse.entity.model;

import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.UuidGenerator;
import es.grupo18.urbanpulse.entity.embeddable.NotificationPreferences;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import jakarta.persistence.*;
import lombok.*;

/**
 * Representa una cuenta de usuario de UrbanPulse.
 * Almacena sus datos de identidad, credenciales, estado de la cuenta,
 * preferencias de notificación y asignaciones de roles globales.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private long tokenVersion;

    @Embedded
    private NotificationPreferences preferences = new NotificationPreferences();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "user")
    @Getter(AccessLevel.NONE)
    private Set<UserRole> userRoles = new HashSet<>();

    public User(String displayName, String email, String passwordHash) {
        this.displayName = displayName;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
    }

    /**
     * Normaliza un correo electrónico eliminando espacios exteriores
     * y convirtiéndolo a minúsculas.
     *
     * @param email correo electrónico que se desea normalizar
     * @return correo normalizado, o null si el argumento es null
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Obtiene los roles globales asignados al usuario.
     * Devuelve una copia ordenada e inmodificable de sus códigos.
     *
     * @return conjunto de códigos de roles globales del usuario
     */
    public Set<RoleCode> globalRoles() {
        Set<RoleCode> codes = new TreeSet<>();
        userRoles.forEach(assignment -> codes.add(assignment.getId().getRoleCode()));
        return Collections.unmodifiableSet(codes);
    }

    /**
     * Actualiza el hash de la contraseña e incrementa la versión del token,
     * permitiendo invalidar los JWT emitidos anteriormente.
     *
     * @param hash nuevo hash de la contraseña
     * @throws ArithmeticException si se desborda la versión del token
     */
    public void changePassword(String hash) {
        tokenVersion = Math.incrementExact(tokenVersion);
        passwordHash = hash;
    }

    /**
     * Establece las fechas de creación y última modificación
     * antes de persistir el usuario por primera vez.
     */
    @PrePersist
    void created() {
        createdAt = updatedAt = Instant.now();
    }

    /**
     * Actualiza la fecha de última modificación
     * antes de guardar cambios en un usuario existente.
     */
    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }
}
