package es.grupo18.urbanpulse.service;

import java.time.*;
import java.util.UUID;
import es.grupo18.urbanpulse.entity.model.*;
import es.grupo18.urbanpulse.exception.ApiException;
import es.grupo18.urbanpulse.repository.*;
import es.grupo18.urbanpulse.service.mail.EmailVerificationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Servicio encargado de gestionar el ciclo de vida de las solicitudes
 * de verificación del correo electrónico.
 * Permite crear, confirmar y reenviar códigos, controlando su caducidad,
 * la validez de la cuenta y la concurrencia entre operaciones.
 * Publica los eventos necesarios para enviar los correos después del commit.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    public static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    public static final Duration ACCOUNT_LIFETIME = Duration.ofHours(24);
    private final UserRepository users;
    private final EmailVerificationRepository verifications;
    private final EmailVerificationCodeService codes;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Crea la solicitud inicial de verificación para un usuario recién registrado.
     * Participa obligatoriamente en la transacción de registro existente,
     * garantizando que el usuario y su solicitud se persistan conjuntamente.
     *
     * @param user usuario recién registrado para el que se emitirá el código
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *                                                                          si
     *                                                                          no
     *                                                                          existe
     *                                                                          una
     *                                                                          transacción
     *                                                                          activa
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createForRegistration(User user) {
        issue(user);
    }

    /**
     * Confirma el correo electrónico de un usuario mediante su código
     * de verificación vigente.
     * Bloquea la cuenta para evitar operaciones concurrentes, comprueba
     * la validez del código y marca el correo como verificado,
     * eliminando la solicitud en la misma transacción.
     * No incrementa la versión del token del usuario.
     *
     * @param id      identificador del usuario que desea confirmar su correo
     * @param version versión del JWT presentado por el usuario
     * @param code    código numérico de verificación proporcionado
     * @throws ApiException si la autenticación no es válida o el código
     *                      es incorrecto, inexistente o ha caducado
     */
    @Transactional
    public void confirm(UUID id, long version, String code) {
        User user = lockedAccount(id, version);

        if (user.isEmailVerified()) {
            return;
        }

        Instant now = clock.instant();
        var request = verifications.findById(id).orElseThrow(EmailVerificationService::invalidCode);

        if (!now.isBefore(user.getCreatedAt().plus(ACCOUNT_LIFETIME))
                || !now.isBefore(request.getExpiresAt())
                || !codes.matches(id, code, request.getCodeHash())) {
            throw invalidCode();
        }

        user.verifyEmail();
        verifications.delete(request);
        users.flush();
    }

    /**
     * Solicita un nuevo código de verificación para el usuario autenticado.
     * Bloquea la cuenta, comprueba su estado y sustituye la solicitud anterior,
     * invalidando el código previo y publicando el evento de envío.
     * No genera un nuevo código si el correo ya está verificado.
     *
     * @param id      identificador del usuario que solicita el reenvío
     * @param version versión del JWT presentado por el usuario
     * @return true si se registra una nueva solicitud de envío; false si
     *         la cuenta ya estaba verificada
     * @throws ApiException si la autenticación no es válida o la cuenta
     *                      pendiente de verificación ha caducado
     */
    @Transactional
    public boolean resend(UUID id, long version) {
        User user = lockedAccount(id, version);

        if (user.isEmailVerified()) {
            return false;
        }

        issue(user);
        return true;
    }

    /**
     * Recupera un usuario aplicando un bloqueo pesimista de escritura.
     * Comprueba que la cuenta esté activa, que la versión del token
     * coincida con la almacenada y que conserve el rol global CITIZEN.
     *
     * @param id      identificador del usuario que se desea bloquear
     * @param version versión del JWT que se debe validar
     * @return usuario bloqueado y autorizado para continuar la operación
     * @throws ApiException si el usuario no existe, está inactivo,
     *                      carece de CITIZEN o presenta una versión no válida
     */
    private User lockedAccount(UUID id, long version) {
        User user = users.findForEmailVerification(id).orElseThrow(ApiException::unauthorized);

        if (!user.isActive() || user.getTokenVersion() != version || !user.globalRoles().contains(
                es.grupo18.urbanpulse.entity.enums.RoleCode.CITIZEN)) {
            throw ApiException.unauthorized();
        }

        return user;
    }

    /**
     * Genera y persiste una nueva solicitud de verificación para un usuario.
     * Calcula la caducidad efectiva del código, limitada a diez minutos
     * y a las 24 horas desde el registro, sustituyendo cualquier código anterior.
     * Publica un evento para enviar el nuevo código después del commit.
     *
     * @param user usuario para el que se generará la solicitud de verificación
     * @throws ApiException si la cuenta ha superado el plazo máximo
     *                      permitido para verificar su correo
     */
    private void issue(User user) {
        Instant now = clock.instant();
        Instant accountExpiry = user.getCreatedAt().plus(ACCOUNT_LIFETIME);

        if (!now.isBefore(accountExpiry)) {
            throw new ApiException(HttpStatus.GONE, "VERIFICATION_ACCOUNT_EXPIRED",
                    "La solicitud de verificación ya no está disponible.");
        }

        Instant expires = now.plus(CODE_LIFETIME);

        if (expires.isAfter(accountExpiry)) {
            expires = accountExpiry;
        }

        var previous = verifications.findById(user.getId());
        String code;

        do {
            code = codes.generate();
        }

        while (previous.isPresent() && codes.matches(user.getId(), code, previous.get().getCodeHash()));

        var request = previous.orElseGet(() -> new EmailVerification(user));
        request.replace(codes.hash(user.getId(), code), now, expires);
        verifications.saveAndFlush(request);
        events.publishEvent(new EmailVerificationRequested(user.getEmail(), code));
    }

    /**
     * Crea una excepción uniforme para los códigos de verificación
     * incorrectos, inexistentes, caducados o invalidados.
     *
     * @return excepción HTTP 422 con el código INVALID_VERIFICATION_CODE
     */
    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_VERIFICATION_CODE",
                "El código de verificación no es válido.");
    }
}
