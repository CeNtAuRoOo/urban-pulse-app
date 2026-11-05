package es.grupo18.urbanpulse.incidents.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.incidents.model.IncidentReview;

/**
 * Persistencia base de revisión; no materializa decisiones de negocio
 * automáticamente.
 */
public interface IncidentReviewRepository extends JpaRepository<IncidentReview, UUID> {
}
