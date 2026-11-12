package es.grupo18.urbanpulse.attachment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.attachment.model.Attachment;

/**
 * Consultas privadas acotadas por incidencia; el servicio comprueba
 * visibilidad.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /**
     * Recupera la colección activa del padre, con desempate por identidad.
     *
     * @param incidentId padre autorizado
     * @return activas en orden estable
     */
    List<Attachment> findByIncident_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID incidentId);

    /**
     * Cuenta las plazas ocupadas, independientemente del autor.
     *
     * @param incidentId padre bloqueado al reservar plaza
     * @return total activo de todos los autores
     */
    long countByIncident_IdAndDeletedAtIsNull(UUID incidentId);

    /**
     * Busca por identidad, pertenencia y ausencia de baja lógica.
     *
     * @param id         evidencia
     * @param incidentId padre autorizado
     * @return activa solo si coincide la pareja
     */
    Optional<Attachment> findByIdAndIncident_IdAndDeletedAtIsNull(UUID id, UUID incidentId);
}
