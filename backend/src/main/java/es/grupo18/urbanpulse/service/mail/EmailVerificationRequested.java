package es.grupo18.urbanpulse.service.mail;

/**
 * Representa un evento de solicitud de envío de un código de verificación.
 * Transporta temporalmente el correo del destinatario y el código
 * en texto plano para permitir su envío después del commit.
 * Su contenido no debe persistirse ni registrarse en los logs.
 *
 * @param email dirección de correo electrónico del destinatario
 * @param code  código numérico de verificación generado para el usuario
 */
public record EmailVerificationRequested(
        String email,
        String code) {

    /**
     * Devuelve una representación del evento sin revelar
     * el destinatario ni el código de verificación.
     *
     * @return representación textual con los datos sensibles ocultos
     */
    @Override
    public String toString() {
        return "EmailVerificationRequested[REDACTED]";
    }
}
