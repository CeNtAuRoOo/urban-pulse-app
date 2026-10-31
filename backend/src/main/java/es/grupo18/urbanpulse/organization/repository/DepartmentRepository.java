package es.grupo18.urbanpulse.organization.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.grupo18.urbanpulse.organization.model.Department;

/** Acceso mínimo al catálogo; no proporciona endpoints de administración. */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    /**
     * Bloquea la unidad para coordinar el permiso con una desactivación
     * concurrente.
     *
     * @param id identificador de departamento
     * @return unidad bloqueada, o vacío
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id")
    Optional<Department> findForInvitation(@Param("id") UUID id);
}
