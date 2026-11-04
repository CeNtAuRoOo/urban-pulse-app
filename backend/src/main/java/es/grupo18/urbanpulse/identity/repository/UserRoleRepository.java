package es.grupo18.urbanpulse.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.identity.model.UserRole;
import es.grupo18.urbanpulse.identity.model.UserRoleId;

/**
 * Repositorio JPA encargado de gestionar las asignaciones de roles
 * globales a usuarios, identificadas mediante una clave primaria compuesta.
 */
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
