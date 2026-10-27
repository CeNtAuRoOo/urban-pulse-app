package es.grupo18.urbanpulse.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de los límites de peticiones para registro e inicio de sesión.
 *
 * @param registerIp política de registro por dirección IP
 * @param loginIp    política de inicio de sesión por dirección IP
 * @param loginEmail política de inicio de sesión por correo electrónico
 * @param maxKeys    número máximo de claves almacenadas por el limitador
 */
@Validated
@ConfigurationProperties("security.rate-limit")
public record RateLimitProperties(
        @NotNull @Valid Policy registerIp,
        @NotNull @Valid Policy loginIp,
        @NotNull @Valid Policy loginEmail,
        @Min(1) int maxKeys) {

    /**
     * Calcula el tiempo de inactividad tras el que puede eliminarse un bucket.
     * Utiliza el mayor tiempo de reposición de las políticas configuradas.
     *
     * @return tiempo de expiración de los buckets, en segundos
     */
    public long ttlSeconds() {
        return Math.max(registerIp.refillSeconds(), Math.max(loginIp.refillSeconds(), loginEmail.refillSeconds()));
    }

    /**
     * Define la capacidad y velocidad de reposición de un bucket de tokens.
     *
     * @param capacity      número máximo de tokens disponibles
     * @param refillSeconds tiempo en segundos para reponer la capacidad completa
     */
    public record Policy(@Min(1) int capacity, @Min(1) long refillSeconds) {
    }
}
