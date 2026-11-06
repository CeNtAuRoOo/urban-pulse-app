package es.grupo18.urbanpulse.attachments;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.attachment.service.ObjectStorageService;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.flyway.enabled=false",
        "security.email-verification.cleanup-cron=-",
        "spring.jpa.hibernate.ddl-auto=none"
})
class NeonAttachmentStorageIT {

    @Autowired
    private ObjectStorageService storage;

    /**
     * Tests uploading, downloading and deleting
     * an object from Neon Object Storage.
     */
    @Test
    void uploadDownloadAndDeleteAttachment() {

        String storageKey = "test-" + UUID.randomUUID();

        byte[] originalData = new byte[] {
                10, 20, 30, 40, 50, 60
        };

        try {
            // 1. Upload
            assertDoesNotThrow(() -> storage.upload(
                    storageKey,
                    originalData,
                    "application/octet-stream"));

            // 2. Download
            byte[] downloadedData = storage.download(storageKey);

            assertNotNull(downloadedData);
            assertArrayEquals(originalData, downloadedData);

        } finally {
            // 3. Delete, even if an assertion fails
            assertDoesNotThrow(() -> storage.delete(storageKey));
        }

        // 4. Verify that the object no longer exists
        assertThrows(
                RuntimeException.class,
                () -> storage.download(storageKey));
    }
}