package es.grupo18.urbanpulse.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de los límites de peticiones de autenticación y verificación.
 *
 * @param registerIp     política de registro por dirección IP
 * @param loginIp        política de inicio de sesión por dirección IP
 * @param loginEmail     política de inicio de sesión por correo electrónico
 * @param confirmUser    intentos de confirmación por cuenta
 * @param confirmIp      intentos de confirmación por IP
 * @param resendCooldown enfriamiento del reenvío por cuenta
 * @param resendUser     presupuesto gradual de reenvíos por cuenta, no cuota
 *                       diaria estricta
 * @param resendIp       reenvíos por IP
 * @param maxKeys        número máximo de claves almacenadas por el limitador
 */
@Validated
@ConfigurationProperties("security.rate-limit")
public record RateLimitProperties(
        @NotNull @Valid Policy registerIp,
        @NotNull @Valid Policy loginIp,
        @NotNull @Valid Policy loginEmail,
        @NotNull @Valid Policy confirmUser,
        @NotNull @Valid Policy confirmIp,
        @NotNull @Valid Policy resendCooldown,
        @NotNull @Valid Policy resendUser,
        @NotNull @Valid Policy resendIp,
        @Min(1) int maxKeys) {

    /**
     * Define la capacidad y velocidad de reposición de un bucket de tokens.
     *
     * @param capacity      número máximo de tokens disponibles
     * @param refillSeconds tiempo en segundos para reponer la capacidad completa
     */
    public record Policy(@Min(1) int capacity, @Min(1) long refillSeconds) {
    }
}
