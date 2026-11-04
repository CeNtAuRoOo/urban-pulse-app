package es.grupo18.urbanpulse.invitations.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import es.grupo18.urbanpulse.shared.mail.EmailMessage;
import es.grupo18.urbanpulse.shared.mail.EmailSender;

/**
 * Entrega sencilla después del commit, sin outbox ni reintentos automáticos.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaffInvitationEmailListener {

    private final EmailSender sender;

    /**
     * Envía instrucciones en español. Un fallo no revierte la invitación
     * confirmada.
     *
     * @param event datos efímeros de entrega; nunca se incluyen en logs
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void send(StaffInvitationRequested event) {
        try {
            String text = "Has recibido una invitación municipal de UrbanPulse para " + event.email()
                    + ".\nRol: " + event.role()
                    + (event.departmentName() == null ? "" : "\nDepartamento: " + event.departmentName())
                    + "\nCódigo: " + event.code()
                    + "\nCaduca en: " + event.expiresAt() + " (UTC)."
                    + "\nSi no tienes cuenta, regístrate y verifica este mismo correo."
                    + " Si ya tienes cuenta verificada, inicia sesión."
                    + "\nCanjea el código con tu sesión en POST /api/v1/users/me/staff-invitation-redemptions."
                    + " El código es de un solo uso. No lo compartas.";

            String html = "<p>" + HtmlUtils.htmlEscape(text).replace("\n", "</p><p>") + "</p>";
            sender.send(new EmailMessage(event.email(), "Invitación municipal de UrbanPulse", text, html));
        }

        catch (MailException ex) {
            log.warn("event=STAFF_INVITATION_EMAIL_FAILED exceptionType={}", ex.getClass().getName());
        }

        catch (RuntimeException ex) {
            log.error("event=STAFF_INVITATION_EMAIL_INTERNAL_ERROR exceptionType={}", ex.getClass().getName());
        }
    }
}
