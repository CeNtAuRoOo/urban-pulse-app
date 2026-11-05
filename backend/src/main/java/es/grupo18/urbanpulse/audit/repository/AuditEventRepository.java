package es.grupo18.urbanpulse.audit.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.audit.model.AuditEvent;

/**
 * Persistencia transaccional de auditoría; sin endpoints de lectura en este
 * módulo.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
