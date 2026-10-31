package es.grupo18.urbanpulse.invitations.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.grupo18.urbanpulse.invitations.model.StaffInvitation;

/**
 * Consultas paginadas SQL y bloqueo de invitaciones sin cargar cuentas
 * destinatarias.
 */
public interface StaffInvitationRepository
        extends JpaRepository<StaffInvitation, UUID>, JpaSpecificationExecutor<StaffInvitation> {

    /**
     * Obtiene una invitación bajo bloqueo para revocación.
     *
     * @param id identificador administrativo
     * @return invitación bloqueada o vacío
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StaffInvitation i where i.id = :id")
    Optional<StaffInvitation> findLockedById(@Param("id") UUID id);

    /**
     * Busca por el índice único HMAC y bloquea antes de evaluar el estado.
     *
     * @param hash HMAC del código; nunca el secreto original
     * @return invitación bloqueada o vacío
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StaffInvitation i where i.codeHash = :hash")
    Optional<StaffInvitation> findLockedByCodeHash(@Param("hash") byte[] hash);
}
