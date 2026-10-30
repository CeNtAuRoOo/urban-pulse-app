package es.grupo18.urbanpulse.repository;

import es.grupo18.urbanpulse.entity.embeddable.UserRoleId;
import es.grupo18.urbanpulse.entity.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA encargado de gestionar las asignaciones de roles
 * globales a usuarios, identificadas mediante una clave primaria compuesta.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
