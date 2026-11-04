package es.grupo18.urbanpulse.organization.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.grupo18.urbanpulse.organization.model.Department;

/** Consultas del catálogo y bloqueos de fila compartidos con Invitaciones. */
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    /**
     * Bloquea la fila hasta el fin de la transacción para serializar PATCH y
     * canjes.
     *
     * @param id UUID de la unidad
     * @return unidad bloqueada, o vacío
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Department d where d.id = :id")
    Optional<Department> findForUpdate(@Param("id") UUID id);

    /**
     * Incluye unidades activas e inactivas en orden de nombre.
     *
     * @return catálogo completo
     */
    List<Department> findAllByOrderByNameAsc();

    /**
     * Comprueba la ocupación del nombre sin distinguir mayúsculas.
     *
     * @param name nombre normalizado
     * @return true si existe
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Comprueba la ocupación del código sin distinguir mayúsculas.
     *
     * @param code código normalizado
     * @return true si existe
     */
    boolean existsByCodeIgnoreCase(String code);

    /**
     * Comprueba nombres ajenos a la unidad modificada.
     *
     * @param name nombre normalizado
     * @param id   unidad excluida
     * @return true si otra unidad usa el nombre
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /**
     * Comprueba códigos ajenos a la unidad modificada.
     *
     * @param code código normalizado
     * @param id   unidad excluida
     * @return true si otra unidad usa el código
     */
    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

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
