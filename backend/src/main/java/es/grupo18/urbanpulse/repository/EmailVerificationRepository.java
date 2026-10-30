package es.grupo18.urbanpulse.repository;

import java.util.UUID;
import es.grupo18.urbanpulse.entity.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA encargado del acceso y persistencia de las solicitudes
 * de verificación del correo electrónico.
 * Cada solicitud se identifica mediante el UUID del usuario asociado
 * y permite consultar, guardar, actualizar y eliminar su código vigente.
 */
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {
}
