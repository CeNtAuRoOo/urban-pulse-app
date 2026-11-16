package es.grupo18.urbanpulse.duplicates.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.incidents.model.Incident;

public interface DuplicateCandidateRepository extends JpaRepository<Incident, UUID> {
    @EntityGraph(attributePaths = { "category" })
    List<Incident> findTop100ByIdNotAndCreatedAtGreaterThanEqualOrderByCreatedAtDescIdAsc(UUID id, Instant since);
}
