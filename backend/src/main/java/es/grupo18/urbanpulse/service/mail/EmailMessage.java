package es.grupo18.urbanpulse.service.mail;

/**
 * Representa un mensaje de correo electrónico que se desea enviar.
 * Contiene los datos necesarios para el transporte y las versiones
 * de texto plano y HTML del contenido.
 *
 * @param to      dirección de correo electrónico del destinatario
 * @param subject asunto del mensaje
 * @param text    contenido del mensaje en texto plano
 * @param html    contenido alternativo del mensaje en HTML
 */
public record EmailMessage(
        String to,
        String subject,
        String text,
        String html) {

    /**
     * Devuelve una representación del mensaje sin revelar su destinatario,
     * asunto ni contenido.
     *
     * @return representación textual con los datos del correo ocultos
     */
    @Override
    public String toString() {
        return "EmailMessage[REDACTED]";
    }
}
