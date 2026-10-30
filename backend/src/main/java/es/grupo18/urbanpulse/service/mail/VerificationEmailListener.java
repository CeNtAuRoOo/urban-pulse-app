package es.grupo18.urbanpulse.service.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.mail.MailException;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;

/**
 * Escucha los eventos de solicitud de verificación del correo electrónico.
 * Prepara y envía los mensajes después de confirmar la transacción
 * de persistencia, evitando que los fallos SMTP reviertan
 * el registro o reenvío previamente confirmado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationEmailListener {

    private final EmailSender sender;

    /**
     * Procesa una solicitud de verificación después del commit de la transacción.
     * Construye el contenido del correo en español y delega su envío
     * en EmailSender, sin participar en una transacción de persistencia activa.
     * Registra los fallos sin revelar información sensible ni propagarlos.
     *
     * @param event evento que contiene el destinatario y el código
     *              de verificación que debe enviarse
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void send(EmailVerificationRequested event) {
        try {
            String text = "Tu código de verificación de UrbanPulse es: " + event.code()
                    + ".\nCaduca en un máximo de 10 minutos y antes de que la cuenta cumpla 24 horas."
                    + "\nSi has solicitado otro código, utiliza el más reciente.";

            String html = "<p>Tu código de verificación de UrbanPulse es:</p><p><strong>" + event.code()
                    + "</strong></p><p>Caduca en un máximo de 10 minutos y antes de que la cuenta cumpla 24 horas."
                    + " Si has solicitado otro código, utiliza el más reciente.</p>";

            sender.send(new EmailMessage(event.email(), "Verifica tu correo en UrbanPulse", text, html));
        }

        catch (MailException ex) {
            log.warn("event=VERIFICATION_EMAIL_FAILED exceptionType={}", ex.getClass().getName());
        }

        catch (RuntimeException ex) {
            log.error("event=VERIFICATION_EMAIL_INTERNAL_ERROR exceptionType={}", ex.getClass().getName());
        }
    }
}
