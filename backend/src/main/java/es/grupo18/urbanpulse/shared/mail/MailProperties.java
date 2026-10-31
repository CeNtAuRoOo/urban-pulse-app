package es.grupo18.urbanpulse.shared.mail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del remitente utilizado para enviar correos electrónicos
 * desde la aplicación, independiente de las credenciales del servidor SMTP.
 *
 * @param from dirección de correo electrónico del remitente
 */
@Validated
@ConfigurationProperties("app.mail")
public record MailProperties(@NotBlank @Email String from) {
}
