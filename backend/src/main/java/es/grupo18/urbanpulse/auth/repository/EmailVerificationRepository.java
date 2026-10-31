package es.grupo18.urbanpulse.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import es.grupo18.urbanpulse.auth.model.EmailVerification;

/**
 * Repositorio JPA encargado del acceso y persistencia de las solicitudes
 * de verificación del correo electrónico.
 * Cada solicitud se identifica mediante el UUID del usuario asociado
 * y permite consultar, guardar, actualizar y eliminar su código vigente.
 */
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {
}
