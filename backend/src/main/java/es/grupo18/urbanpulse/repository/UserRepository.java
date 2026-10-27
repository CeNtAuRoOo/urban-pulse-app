package es.grupo18.urbanpulse.repository;

import java.util.*;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio JPA encargado del acceso y persistencia de usuarios.
 * Proporciona consultas para autenticación, obtención de roles
 * y actualización segura de contraseñas.
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
     * Consulta el estado de seguridad de un usuario y sus roles globales actuales.
     * Devuelve una fila por asignación de rol, incluso si no tiene
     * ninguna, para comprobar la validez de sus JWT.
     *
     * @param id identificador del usuario
     * @return filas con el estado de la cuenta, versión del token y roles
     */
    @Query("""
            select u.active as active, u.tokenVersion as tokenVersion, r.id.roleCode as roleCode
            from User u
            left join u.userRoles r
            where u.id = :id
            """)
    List<SecurityState> findSecurityState(@Param("id") UUID id);

    /**
     * Proyección de los datos de seguridad necesarios para autenticar
     * un usuario, incluyendo su estado, versión del token y rol global.
     * El código del rol puede ser null si no existen asignaciones.
     */
    interface SecurityState {
        boolean getActive();

        long getTokenVersion();

        RoleCode getRoleCode();
    }
}
