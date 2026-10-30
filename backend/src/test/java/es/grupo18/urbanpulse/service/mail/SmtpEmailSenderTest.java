package es.grupo18.urbanpulse.service.mail;

import java.util.Properties;
import es.grupo18.urbanpulse.config.MailProperties;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class SmtpEmailSenderTest {
    @Test
    void listenerContainsUnexpectedAdapterFailuresWithoutExposingEventOrCause(CapturedOutput output) {
        var sender = mock(EmailSender.class);
        doThrow(new IllegalStateException("PRIVATE_SMTP_SECRET")).when(sender).send(any());
        new VerificationEmailListener(sender).send(new EmailVerificationRequested("private@example.org", "01234567"));
        assertThat(output.getAll()).contains("event=VERIFICATION_EMAIL_INTERNAL_ERROR")
                .doesNotContain("PRIVATE_SMTP_SECRET", "private@example.org", "01234567");
    }

    @Test
    void buildsUtf8MultipartWithConfiguredFromAndPlainAndHtmlAlternatives() throws Exception {
        var mail = mock(JavaMailSender.class);
        when(mail.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        var sender = new SmtpEmailSender(mail, new MailProperties("no-reply@urbanpulse.test"));
        sender.send(new EmailMessage("registered@example.org", "Verifica tu correo", "Código: 01234567",
                "<p>Código: <strong>01234567</strong></p>"));
        var capture = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(capture.capture());
        MimeMessage message = capture.getValue();
        message.saveChanges();
        assertThat(message.getAllRecipients()).containsExactly(new InternetAddress("registered@example.org"));
        assertThat(message.getFrom()).containsExactly(new InternetAddress("no-reply@urbanpulse.test"));
        assertThat(message.getSubject()).isEqualTo("Verifica tu correo");
        String content = extractText(message);
        assertThat(content).contains("Código: 01234567", "<strong>01234567</strong>");
    }

    private String extractText(Part part) throws Exception {
        if (part.getContent() instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++)
                text.append(extractText(multipart.getBodyPart(i)));
            return text.toString();
        }
        return part.getContent().toString();
    }
}
