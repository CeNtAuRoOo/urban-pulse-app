package es.grupo18.urbanpulse.audit.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.audit.model.AuditEvent;

/**
 * Persistencia transaccional de auditoría; sin endpoints de lectura en este
 * módulo.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    /**
     * Filtra antes de paginar y carga solo el actor to-one, sin roles ni
     * pertenencias.
     *
     * @param resourceType tipo lógico exacto
     * @param resourceId   identidad exacta del recurso
     * @param actions      lista cerrada de acciones visibles elegida por el caso de
     *                     uso
     * @param pageable     límites y orden explícitos
     * @return eventos permitidos, sin mezclar recursos ni acciones internas
     */
    @EntityGraph(attributePaths = "actor")
    Page<AuditEvent> findByResourceTypeAndResourceIdAndActionIn(String resourceType, String resourceId,
            Collection<String> actions, Pageable pageable);
}
