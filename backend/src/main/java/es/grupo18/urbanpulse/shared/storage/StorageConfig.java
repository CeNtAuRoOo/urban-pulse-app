package es.grupo18.urbanpulse.shared.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Cliente S3-compatible privado, con credenciales estándar AWS y tiempos
 * acotados.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    /**
     * Configura el cliente privado con endpoint externo y tiempos acotados.
     *
     * @param properties configuración externa
     * @return cliente path-style sin credenciales embebidas
     */
    @Bean
    S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .forcePathStyle(true)
                .overrideConfiguration(c -> c.apiCallTimeout(java.time.Duration.ofSeconds(60))
                        .apiCallAttemptTimeout(java.time.Duration.ofSeconds(30)))
                .build();

    }
}
