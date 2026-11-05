package es.grupo18.urbanpulse.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.organization.model.DepartmentMembership;

/**
 * Repositorio JPA encargado de consultar las pertenencias departamentales
 * de los usuarios, incluyendo sus datos de departamento y rol.
 * Permite recuperar las adscripciones de varios usuarios en una única consulta
 * para evitar consultas N+1 al construir respuestas paginadas.
 */
public interface DepartmentMembershipRepository extends JpaRepository<DepartmentMembership, UUID> {

    /**
     * Obtiene las pertenencias históricas y activas de un conjunto de usuarios.
     * Carga el departamento y el rol asociados para construir los DTO sin
     * acceder posteriormente a relaciones lazy.
     *
     * @param userIds identificadores de los usuarios cuyas pertenencias se
     *                consultan
     * @return pertenencias ordenadas por fecha de alta descendente e identificador
     *         ascendente
     */
    @EntityGraph(attributePaths = { "department", "role" })
    @Query("""
            select membership
            from DepartmentMembership membership
            where membership.user.id in :userIds
            order by membership.joinedAt desc, membership.id asc
            """)
    List<DepartmentMembership> findAllForUsers(@Param("userIds") Collection<UUID> userIds);

    /**
     * Comprueba si existe una pertenencia activa para la misma combinación
     * de usuario, departamento y rol.
     *
     * @param userId       identificador del usuario
     * @param departmentId identificador del departamento
     * @param roleCode     código del rol departamental
     * @return true si ya existe una pertenencia activa con esa combinación
     */
    boolean existsByUserIdAndDepartmentIdAndRoleCodeAndLeftAtIsNull(
            UUID userId,
            UUID departmentId,
            RoleCode roleCode);

    /**
     * Busca una pertenencia aplicando un bloqueo pesimista de escritura
     * para serializar su finalización frente a otras operaciones concurrentes.
     *
     * @param id identificador de la pertenencia departamental
     * @return pertenencia bloqueada, o un Optional vacío si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from DepartmentMembership membership where membership.id = :id")
    Optional<DepartmentMembership> findForUpdate(@Param("id") UUID id);

    /**
     * Consulta únicamente el titular inmutable, sin cargar estado mutable en JPA.
     * Permite bloquear usuario antes que pertenencia sin conservar una baja
     * obsoleta.
     *
     * @param id identificador de la pertenencia
     * @return identificador del titular, o vacío si no existe la pertenencia
     */
    @Query("select m.user.id from DepartmentMembership m where m.id = :id")
    Optional<UUID> findOwnerId(@Param("id") UUID id);
}
