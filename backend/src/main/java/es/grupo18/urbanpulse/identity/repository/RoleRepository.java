package es.grupo18.urbanpulse.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.RoleCode;

/**
 * Repositorio JPA encargado del acceso y persistencia del catálogo
 * de roles de UrbanPulse, identificados mediante su código.
 */
public interface RoleRepository extends JpaRepository<Role, RoleCode> {
}
