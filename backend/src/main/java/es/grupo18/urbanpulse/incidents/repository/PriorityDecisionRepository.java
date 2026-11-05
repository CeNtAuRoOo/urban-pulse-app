package es.grupo18.urbanpulse.incidents.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.incidents.model.PriorityDecision;

/** Persistencia base de prioridad; la operación de priorizar queda aplazada. */
public interface PriorityDecisionRepository extends JpaRepository<PriorityDecision, UUID> {
}
