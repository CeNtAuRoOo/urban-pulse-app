package es.grupo18.urbanpulse.attachments;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.attachment.service.ObjectStorageService;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Real Neon Object Storage integration test: PUT -> GET -> DELETE against the
 * configured S3-compatible endpoint.
 *
 * Requires all of AWS_ENDPOINT_URL_S3, AWS_ACCESS_KEY_ID,
 * AWS_SECRET_ACCESS_KEY,
 * AWS_REGION and STORAGE_BUCKET to be present and non-empty in the environment.
 * The class is skipped entirely otherwise, so it can live in the repo without
 * breaking CI or a plain `mvn verify -Pneon-storage` on a machine with no
 * creds.
 */
@EnabledIfEnvironmentVariable(named = "AWS_ENDPOINT_URL_S3", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_REGION", matches = ".+")
@EnabledIfEnvironmentVariable(named = "STORAGE_BUCKET", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "security.email-verification.cleanup-cron=-",
        "spring.mail.port=2525"
})
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class NeonAttachmentStorageIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ObjectStorageService storage;

    @Test
    void uploadDownloadAndDeleteAttachment() {

        String storageKey = "test-" + UUID.randomUUID();
        byte[] originalData = new byte[] { 10, 20, 30, 40, 50, 60 };

        storage.upload(storageKey, originalData, "application/octet-stream");

        try {
            byte[] downloaded = storage.download(storageKey);
            assertThat(downloaded).isNotNull().isEqualTo(originalData);
        } finally {
            storage.delete(storageKey);
        }

        // S3 GET on a deleted key throws NoSuchKeyException (subclass of S3Exception),
        // it does not return null.
        assertThatThrownBy(() -> storage.download(storageKey))
                .isInstanceOf(S3Exception.class);
    }
}
