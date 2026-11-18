package es.grupo18.urbanpulse.processing.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.processing.model.StatusChange;

/**
 * Historial de transiciones efectivas, escrito atómicamente por ProcessingService.
 */
public interface StatusChangeRepository extends JpaRepository<StatusChange, UUID> {
}
