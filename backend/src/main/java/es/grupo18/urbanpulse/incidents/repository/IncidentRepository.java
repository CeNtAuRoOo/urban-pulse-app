package es.grupo18.urbanpulse.incidents.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import es.grupo18.urbanpulse.incidents.model.Incident;

/**
 * Consultas paginadas SQL con relaciones to-one; nunca pagina colecciones en
 * memoria.
 */
public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    /**
     * Serializa altas y bajas de evidencias entre instancias sobre la fila padre.
     * No carga joins opcionales que PostgreSQL no pueda bloquear.
     *
     * @param id incidencia a bloquear dentro de la transacción del servicio
     * @return fila bloqueada, o vacío
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select i from Incident i where i.id = :id")
    Optional<Incident> findForAttachments(@org.springframework.data.repository.query.Param("id") UUID id);

    /**
     * Carga relaciones to-one para detalle y precondición sin consultas lazy
     * posteriores.
     * La visibilidad se comprueba en el servicio, no en este método.
     *
     * @param id identidad solicitada
     * @return entidad con relaciones del DTO, o vacío si no existe
     */
    @Override
    @EntityGraph(attributePaths = { "reporter", "category", "assignedDepartment" })
    Optional<Incident> findById(UUID id);

    /**
     * Pagina sin fetch de colecciones; el catálogo se carga para los resúmenes.
     *
     * @param spec     filtros SQL de titularidad, estado, categoría y fechas
     * @param pageable límites y orden estable resueltos por el servicio
     * @return página filtrada en SQL, sin N+1 de categoría y sin cargar roles
     */
    @Override
    @EntityGraph(attributePaths = { "category" })
    Page<Incident> findAll(Specification<Incident> spec, Pageable pageable);
}
