package es.grupo18.urbanpulse.processing.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.processing.model.IncidentInformationResponse;

/**
 * Persistencia histórica; todas las consultas públicas se acotan al padre en
 * SQL.
 */
public interface IncidentInformationResponseRepository extends JpaRepository<IncidentInformationResponse, UUID> {

    /**
     * Devuelve página SQL con responsable, sin N+1.
     *
     * @param incidentId padre autorizado
     * @param page       paginación y orden temporal estable
     * @return página SQL con responsable, sin N+1
     */
    @EntityGraph(attributePaths = { "respondedBy" })
    Page<IncidentInformationResponse> findByIncident_Id(UUID incidentId, Pageable page);

    /**
     * Devuelve recurso de ese padre, nunca de otro.
     *
     * @param id         recurso anidado
     * @param incidentId padre autorizado
     * @return recurso de ese padre, nunca de otro
     */
    @EntityGraph(attributePaths = { "respondedBy" })
    Optional<IncidentInformationResponse> findByIdAndIncident_Id(UUID id, UUID incidentId);

    /**
     * Devuelve si ya fue consumida.
     *
     * @param reviewId solicitud exacta
     * @return si ya fue consumida
     */
    boolean existsByReview_Id(UUID reviewId);
}
