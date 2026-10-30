
package es.grupo18.urbanpulse.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Configura el cliente de almacenamiento de objetos compatible con Amazon S3,
 * utilizado para acceder al servicio de almacenamiento de Neon.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    /**
     * Crea el cliente S3 utilizando el endpoint y la región configurados.
     * Habilita el acceso a los buckets mediante rutas (path-style).
     *
     * @param properties configuración del servicio de almacenamiento
     * @return cliente S3 configurado
     */
    @Bean
    public S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build())
                .build();
    }
}
