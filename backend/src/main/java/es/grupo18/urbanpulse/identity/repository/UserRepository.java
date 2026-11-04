package es.grupo18.urbanpulse.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;

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
     * un usuario, incluyendo actividad, verificación del correo,
     * versión del token y rol global.
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

    /**
     * Busca un usuario por su identificador y carga sus asignaciones de roles.
     * Evita consultas adicionales al acceder posteriormente a los roles.
     *
     * @param id identificador del usuario
     * @return usuario encontrado con sus roles, o un Optional vacío si no existe
     */
    @EntityGraph(attributePaths = "userRoles")
    @Query("select u from User u where u.id = :id")
    Optional<User> findUserWithRolesById(@Param("id") UUID id);

    /**
     * Busca usuarios aplicando filtros opcionales por rol global o por rol
     * departamental activo y estado de actividad.
     * Los resultados se devuelven de forma paginada y ordenada
     * según el objeto Pageable recibido.
     *
     * @param role     rol global o departamental activo que deben tener los
     *                 usuarios, o null para no filtrar
     * @param active   estado de actividad requerido, o null para no filtrar
     * @param pageable configuración de paginación y ordenación
     * @return página con los usuarios que cumplen los filtros
     */
    @Query("""
            select u
            from User u
            where (:active is null or u.active = :active)
              and (
                  :role is null
                  or exists (
                      select ur
                      from UserRole ur
                      where ur.user = u
                        and ur.id.roleCode = :role
                  )
                  or exists (
                      select membership
                      from DepartmentMembership membership
                      where membership.user = u
                        and membership.role.code = :role
                        and membership.leftAt is null
                  )
              )
            """)
    Page<User> searchUsers(
            @Param("role") RoleCode role,
            @Param("active") Boolean active,
            Pageable pageable);

    /**
     * Carga los roles de los usuarios ya seleccionados por la página SQL.
     * No pagina un fetch de colección: mantiene el límite y los totales originales.
     *
     * @param ids identificadores de una página no vacía
     * @return usuarios con sus roles inicializados en el contexto de persistencia
     */
    @EntityGraph(attributePaths = "userRoles")
    @Query("select u from User u where u.id in :ids")
    List<User> findAllWithRolesByIdIn(@Param("ids") List<UUID> ids);

    /**
     * Serializa cambios administrativos de actividad antes de bloquear usuarios.
     * El bloqueo transaccional PostgreSQL se libera tanto al confirmar como al
     * abortar.
     *
     * @return true al adquirir el bloqueo compartido por la administración de
     *         cuentas
     */
    @Query(value = "select true from pg_advisory_xact_lock(182011, 1)", nativeQuery = true)
    boolean lockAccountAdministration();

    /**
     * Cuenta administradores globales que todavía pueden acceder a la API.
     * Se consulta bajo el bloqueo de administración para evitar bajas simultáneas.
     *
     * @return cuentas activas, verificadas y con asignación global ADMIN
     */
    @Query("select count(u) from User u where u.active = true and u.emailVerified = true "
            + "and exists (select r from UserRole r where r.user = u and r.id.roleCode = es.grupo18.urbanpulse.identity.model.RoleCode.ADMIN)")
    long countAvailableAdministrators();
}
