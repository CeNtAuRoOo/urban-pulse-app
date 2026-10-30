package es.grupo18.urbanpulse.repository;

import java.util.*;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio JPA encargado del acceso y persistencia de usuarios.
 * Proporciona consultas para autenticación, obtención del estado de seguridad,
 * gestión de roles y actualización segura de contraseñas y verificación
 * del correo electrónico.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca un usuario por su correo electrónico, cargando también
     * sus asignaciones de roles globales.
     *
     * @param email correo electrónico normalizado del usuario
     * @return usuario encontrado, o un Optional vacío si no existe
     */
    @EntityGraph(attributePaths = "userRoles")
    Optional<User> findByEmail(String email);

    /**
     * Busca un usuario aplicando un bloqueo pesimista de escritura
     * para evitar modificaciones concurrentes durante el cambio de contraseña.
     *
     * @param id identificador del usuario
     * @return usuario bloqueado, o un Optional vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForPasswordChange(@Param("id") UUID id);

    /**
     * Busca un usuario aplicando un bloqueo pesimista de escritura
     * para coordinar las operaciones concurrentes de confirmación,
     * reenvío y eliminación de cuentas pendientes de verificación.
     *
     * @param id identificador del usuario que se desea bloquear
     * @return usuario bloqueado, o un Optional vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForEmailVerification(@Param("id") UUID id);

    /**
     * Consulta el estado de seguridad de un usuario y sus roles globales actuales.
     * Devuelve una fila por asignación de rol, incluso si no tiene
     * ninguna, para comprobar la validez de sus JWT.
     *
     * @param id identificador del usuario
     * @return filas con actividad, verificación del correo, versión del token y
     *         roles
     */
    @Query("""
            select u.active as active, u.emailVerified as emailVerified,
                   u.tokenVersion as tokenVersion, r.id.roleCode as roleCode
            from User u
            left join u.userRoles r
            where u.id = :id
            """)
    List<SecurityState> findSecurityState(@Param("id") UUID id);

    /**
     * Proyección de los datos de seguridad necesarios para autenticar
     * un usuario, incluyendo actividad, verificación de correo, versión y rol
     * global.
     * El código del rol puede ser null si no existen asignaciones.
     */
    interface SecurityState {
        /**
         * Obtiene el estado de actividad de la cuenta.
         *
         * @return true si la cuenta está activa; false en caso contrario
         */
        boolean getActive();

        /**
         * Obtiene el estado actual de verificación del correo electrónico.
         *
         * @return true si el correo está verificado; false en caso contrario
         */
        boolean getEmailVerified();

        /**
         * Obtiene la versión actual de los tokens del usuario.
         *
         * @return versión del token almacenada en la base de datos
         */
        long getTokenVersion();

        /**
         * Obtiene el código del rol asociado a la fila de seguridad.
         *
         * @return código del rol global, o null si no existe una asignación
         */
        RoleCode getRoleCode();
    }
}
