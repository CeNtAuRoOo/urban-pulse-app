package es.grupo18.urbanpulse.service.mail;

import es.grupo18.urbanpulse.config.MailProperties;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;

/**
 * Implementación del envío de correos electrónicos mediante SMTP.
 * Utiliza JavaMailSender para construir y enviar mensajes MIME
 * con contenido en texto plano y HTML, empleando el remitente
 * configurado por la aplicación.
 */
@Component
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mail;
    private final MailProperties properties;

    /**
     * Construye y envía un correo electrónico mediante JavaMailSender.
     * Configura la codificación UTF-8, el remitente, destinatario,
     * asunto y las alternativas de contenido en texto plano y HTML.
     *
     * @param message mensaje de correo electrónico que se desea enviar
     * @throws MailPreparationException               si no se puede preparar el
     *                                                mensaje MIME
     * @throws org.springframework.mail.MailException si falla el transporte SMTP
     */
    @Override
    public void send(EmailMessage message) {
        try {
            var mime = mail.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(properties.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mail.send(mime);
        }

        catch (MessagingException ex) {
            throw new MailPreparationException("No se pudo preparar el correo.");
        }
    }
}
