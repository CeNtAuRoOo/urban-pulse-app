
package es.grupo18.urbanpulse.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuración de conexión al servicio de almacenamiento de objetos S3.
 *
 * @param endpoint dirección del servicio de almacenamiento
 * @param region   región utilizada por el cliente S3
 * @param bucket   nombre del bucket de almacenamiento
 */
@Validated
@ConfigurationProperties(prefix = "storage.s3")
public record StorageProperties(
        @NotNull URI endpoint,
        @NotBlank String region,
        @NotBlank String bucket) {
}
