package es.grupo18.urbanpulse.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import es.grupo18.urbanpulse.shared.mail.MailProperties;

/**
 * Configura los componentes necesarios para la verificación del correo
 * electrónico. Habilita las tareas programadas de limpieza y registra
 * las propiedades de verificación y envío de correos.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ EmailVerificationProperties.class, MailProperties.class })
public class EmailVerificationConfig {
}
