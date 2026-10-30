package es.grupo18.urbanpulse.repository;

import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA encargado del acceso y persistencia del catálogo
 * de roles de UrbanPulse, identificados mediante su código.
 */
public interface RoleRepository extends JpaRepository<Role, RoleCode> {
}
