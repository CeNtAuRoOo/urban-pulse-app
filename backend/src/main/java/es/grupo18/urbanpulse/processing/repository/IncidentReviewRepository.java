package es.grupo18.urbanpulse.processing.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.processing.model.IncidentReview;

/**
 * Persistencia histórica; todas las consultas públicas se acotan al padre en SQL.
 */
public interface IncidentReviewRepository extends JpaRepository<IncidentReview, UUID> {

    /**
     * Devuelve página SQL con responsable, sin N+1.
     *
     * @param incidentId padre autorizado
     * @param page       paginación y orden temporal estable
     * @return página SQL con responsable, sin N+1
     */
    @EntityGraph(attributePaths = { "reviewedBy" })
    Page<IncidentReview> findByIncident_Id(UUID incidentId, Pageable page);

    /**
     * Devuelve recurso de ese padre, nunca de otro.
     *
     * @param id         recurso anidado
     * @param incidentId padre autorizado
     * @return recurso de ese padre, nunca de otro
     */
    @EntityGraph(attributePaths = { "reviewedBy" })
    Optional<IncidentReview> findByIdAndIncident_Id(UUID id, UUID incidentId);

    /**
     * Devuelve última revisión histórica.
     *
     * @param incidentId padre autorizado
     * @return última revisión histórica
     */
    Optional<IncidentReview> findFirstByIncident_IdOrderByReviewedAtDescIdDesc(UUID incidentId);
}
