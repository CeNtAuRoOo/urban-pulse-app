package es.grupo18.urbanpulse.identity.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;

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

    /**
     * Identificador único del usuario.
     * Se genera automáticamente utilizando UUID versión 7.
     */
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /**
     * Nombre público que identifica al usuario dentro de la aplicación.
     */
    @Column(nullable = false, length = 120)
    private String displayName;

    /**
     * Dirección de correo electrónico asociada a la cuenta.
     * Se almacena normalizada para evitar diferencias por mayúsculas
     * o espacios exteriores.
     */
    @Column(nullable = false, length = 254)
    private String email;

    /**
     * Hash de la contraseña del usuario.
     * Almacena el resultado del proceso de hash y nunca la contraseña original.
     */
    @Column(nullable = false, length = 60)
    private String passwordHash;

    /**
     * Indica si la cuenta del usuario está activa.
     * Por defecto, las nuevas cuentas se crean como activas.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Indica si el usuario ha verificado su dirección de correo electrónico.
     */
    @Column(nullable = false)
    private boolean emailVerified;

    /**
     * Versión de los tokens de autenticación del usuario.
     * Permite invalidar los JWT anteriores cuando se incrementa su valor.
     */
    @Column(nullable = false)
    private long tokenVersion;

    /**
     * Preferencias de notificación asociadas al usuario.
     * Se almacenan como parte de la propia entidad mediante un objeto embebido.
     */
    @Embedded
    private NotificationPreferences preferences = new NotificationPreferences();

    /**
     * Fecha y hora de creación de la cuenta.
     * Se establece automáticamente al persistir el usuario por primera vez.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Fecha y hora de la última modificación de la cuenta.
     * Se actualiza automáticamente cuando se modifica el usuario.
     */
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Asignaciones de roles globales asociadas al usuario.
     * La relación se establece mediante el atributo user de UserRole.
     * No se genera un getter público directo para proteger la colección.
     */
    @OneToMany(mappedBy = "user")
    @Getter(AccessLevel.NONE)
    private Set<UserRole> userRoles = new HashSet<>();

    /**
     * Crea un nuevo usuario con su nombre público, correo normalizado
     * y hash de contraseña.
     * Inicializa la cuenta como activa, con el correo sin verificar
     * y la versión inicial del token.
     *
     * @param displayName  nombre público del usuario
     * @param email        correo electrónico del usuario
     * @param passwordHash hash de la contraseña del usuario
     * @throws es.grupo18.urbanpulse.shared.error.ApiException si el nombre
     *                                                         normalizado no es
     *                                                         válido
     */
    public User(String displayName, String email, String passwordHash) {
        this.displayName = DisplayNames.requireValid(displayName);
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
     * Marca el correo electrónico del usuario como verificado.
     * No modifica la versión del token, permitiendo que los JWT
     * existentes sigan siendo válidos tras la confirmación.
     */
    public void verifyEmail() {
        emailVerified = true;
    }

    /**
     * Establece las fechas de creación y última modificación
     * antes de persistir el usuario por primera vez.
     * Este método es invocado automáticamente por JPA.
     */
    @PrePersist
    void created() {
        createdAt = updatedAt = Instant.now();
    }

    /**
     * Actualiza la fecha de última modificación antes de guardar
     * los cambios realizados en un usuario existente.
     * Este método es invocado automáticamente por JPA.
     */
    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    /**
     * Actualiza el nombre público del usuario recortando espacios Unicode de borde
     * y validando longitud y controles antes de cambiar el estado.
     *
     * @param displayName nuevo nombre público del usuario
     * @throws es.grupo18.urbanpulse.shared.error.ApiException si el nombre
     *                                                         normalizado no es
     *                                                         válido
     */
    public void updateDisplayName(String displayName) {
        this.displayName = DisplayNames.requireValid(displayName);
    }

    /**
     * Modifica el estado de actividad de la cuenta.
     *
     * @param active true para activar la cuenta; false para desactivarla
     */
    public void setActive(boolean active) {
        this.active = active;
    }
}
