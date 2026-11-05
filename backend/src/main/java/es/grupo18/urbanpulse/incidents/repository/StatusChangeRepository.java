package es.grupo18.urbanpulse.incidents.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.incidents.model.StatusChange;

/**
 * Persistencia base de transiciones; sin servicios ni escrituras desde la API
 * actual.
 */
public interface StatusChangeRepository extends JpaRepository<StatusChange, UUID> {
}
