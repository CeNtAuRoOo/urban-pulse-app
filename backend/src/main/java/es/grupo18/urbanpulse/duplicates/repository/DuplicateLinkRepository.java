package es.grupo18.urbanpulse.duplicates.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.grupo18.urbanpulse.duplicates.model.DuplicateLink;

public interface DuplicateLinkRepository extends JpaRepository<DuplicateLink, UUID> {
    @Query("select case when count(d) > 0 then true else false end from DuplicateLink d "
            + "where (d.incident.id = :first and d.candidateIncident.id = :second) "
            + "or (d.incident.id = :second and d.candidateIncident.id = :first)")
    boolean existsBetween(@Param("first") UUID first, @Param("second") UUID second);

    @Query("select d.candidateIncident.id from DuplicateLink d where d.incident.id = :id")
    List<UUID> linkedCandidateIds(@Param("id") UUID id);

    @Query("select d.incident.id from DuplicateLink d where d.candidateIncident.id = :id")
    List<UUID> linkedSourceIds(@Param("id") UUID id);

    @EntityGraph(attributePaths = { "linkedBy" })
    @Query("select d from DuplicateLink d where d.incident.id = :id or d.candidateIncident.id = :id order by d.linkedAt desc")
    List<DuplicateLink> findLinksForIncident(@Param("id") UUID id);
}
