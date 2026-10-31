package es.grupo18.urbanpulse;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import es.grupo18.urbanpulse.dto.attachment.AttachmentDTO;
import es.grupo18.urbanpulse.entity.model.Attachment;
import es.grupo18.urbanpulse.entity.model.User;
import es.grupo18.urbanpulse.exception.ApiException;
import es.grupo18.urbanpulse.repository.AttachmentRepository;
import es.grupo18.urbanpulse.service.AttachmentService;
import es.grupo18.urbanpulse.service.ObjectStorageService;
import es.grupo18.urbanpulse.service.attachment.AttachmentContent;

@SpringBootTest(
    properties = {
        "spring.flyway.enabled=false",
        "security.email-verification.cleanup-cron=-",
        "spring.jpa.hibernate.ddl-auto=none"
    }
)
@Transactional
class AttachmentServiceIT {

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private ObjectStorageService storage;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    /**
     * Creates a test user before every test.
     *
     * Uses the application's configured PasswordEncoder
     * to generate a valid password hash.
     */
    @BeforeEach
    void setUp() {

        String encodedPassword =
                passwordEncoder.encode("test-password");

        testUser = new User(
                "Attachment Test User",
                "attachment-test-" + UUID.randomUUID() + "@example.com",
                encodedPassword
        );

        entityManager.persist(testUser);
        entityManager.flush();
    }

    /**
     * Removes uploaded objects from storage after every test.
     *
     * Database changes are automatically rolled back.
     */
    @AfterEach
    void cleanUp() {

        if (testUser == null) {
            return;
        }

        /*List<Attachment> userAttachments =
                attachmentRepository.findAllByUser(testUser);

        for (Attachment attachment : userAttachments) {
            storage.delete(attachment.getStorageKey());
        }*/
    }

    /**
     * Generates a real JPEG image for testing.
     */
    private byte[] createTestJpeg() throws Exception {

        BufferedImage image = new BufferedImage(
                100,
                100,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = image.createGraphics();

        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, 100, 100);

            graphics.setColor(Color.WHITE);
            graphics.fillRect(25, 25, 50, 50);

        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertTrue(ImageIO.write(image, "jpeg", output));

        return output.toByteArray();
    }

    /**
     * Creates a MultipartFile containing a valid JPEG image.
     */
    private MockMultipartFile createValidFile() throws Exception {

        return new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                createTestJpeg()
        );
    }

    // ---------------------------------------------------------
    // UPLOAD TESTS
    // ---------------------------------------------------------

    @Test
    void uploadValidJpegCreatesAttachment() throws Exception {

        MockMultipartFile file = createValidFile();

        AttachmentDTO result = attachmentService.attachPhoto(
                file,
                "Test photograph",
                testUser
        );

        assertNotNull(result);
        assertNotNull(result.id());

        assertEquals("test-image.jpg", result.fileName());
        assertEquals("image/jpeg", result.contentType());
        assertTrue(result.sizeBytes() > 0);
        assertEquals("Test photograph", result.description());

        Attachment saved = attachmentRepository
                .findById(result.id())
                .orElseThrow();

        assertEquals(testUser.getId(), saved.getUser().getId());
        assertEquals("test-image.jpg", saved.getOriginalFilename());

        byte[] downloaded = storage.download(saved.getStorageKey());

        assertNotNull(downloaded);
        assertTrue(downloaded.length > 0);
    }

    @Test
    void uploadWithoutDescriptionWorks() throws Exception {

        AttachmentDTO result = attachmentService.attachPhoto(
                createValidFile(),
                null,
                testUser
        );

        assertNotNull(result.id());
        assertNull(result.description());
    }

    // ---------------------------------------------------------
    // DOWNLOAD TESTS
    // ---------------------------------------------------------

    @Test
    void downloadAttachmentReturnsCorrectContent() throws Exception {

        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "Download test",
                testUser
        );

        AttachmentContent content =
                attachmentService.getAttachmentContent(created.id());

        assertNotNull(content);
        assertNotNull(content.data());

        assertTrue(content.data().length > 0);
        assertEquals("test-image.jpg", content.fileName());
        assertEquals("image/jpeg", content.contentType());
        assertEquals(content.data().length, content.sizeBytes());

        BufferedImage decoded = ImageIO.read(
                new java.io.ByteArrayInputStream(content.data())
        );

        assertNotNull(decoded);
        assertEquals(100, decoded.getWidth());
        assertEquals(100, decoded.getHeight());
    }

    // ---------------------------------------------------------
    // RETRIEVAL TESTS
    // ---------------------------------------------------------

    @Test
    void getAttachmentReturnsCorrectMetadata() throws Exception {

        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "Metadata test",
                testUser
        );

        AttachmentDTO result =
                attachmentService.getAttachment(created.id());

        assertEquals(created.id(), result.id());
        assertEquals(created.fileName(), result.fileName());
        assertEquals(created.contentType(), result.contentType());
        assertEquals(created.description(), result.description());
    }

    @Test
    void getAttachmentsReturnsUploadedAttachment() throws Exception {

        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "List test",
                testUser
        );

        List<AttachmentDTO> results =
                attachmentService.getAttachments();

        assertTrue(
                results.stream().anyMatch(
                        attachment -> attachment.id().equals(created.id())
                )
        );
    }

    @Test
    void retrievingNonexistentAttachmentThrowsException() {

        UUID nonexistentId = UUID.randomUUID();

        assertThrows(
                ApiException.class,
                () -> attachmentService.getAttachment(nonexistentId)
        );
    }

    // ---------------------------------------------------------
    // VALIDATION TESTS
    // ---------------------------------------------------------

    @Test
    void uploadingEmptyFileThrowsException() {

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(
                ApiException.class,
                () -> attachmentService.attachPhoto(
                        emptyFile,
                        null,
                        testUser
                )
        );
    }

    @Test
    void uploadingInvalidFormatThrowsException() {

        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "invalid.txt",
                "text/plain",
                "This is not an image".getBytes()
        );

        assertThrows(
                ApiException.class,
                () -> attachmentService.attachPhoto(
                        invalidFile,
                        null,
                        testUser
                )
        );
    }

    @Test
    void descriptionOver300CharactersThrowsException() throws Exception {

        String description = "A".repeat(301);

        assertThrows(
                ApiException.class,
                () -> attachmentService.attachPhoto(
                        createValidFile(),
                        description,
                        testUser
                )
        );
    }

    @Test
    void nullUploaderThrowsException() throws Exception {

        assertThrows(
                ApiException.class,
                () -> attachmentService.attachPhoto(
                        createValidFile(),
                        null,
                        null
                )
        );
    }

    // ---------------------------------------------------------
    // DELETE TESTS
    // ---------------------------------------------------------

    @Test
    void deletingAttachmentMarksItAsDeleted() throws Exception {

        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "Delete test",
                testUser
        );

        Attachment beforeDeletion = attachmentRepository
                .findById(created.id())
                .orElseThrow();

        String storageKey = beforeDeletion.getStorageKey();

        attachmentService.deleteAttachment(created.id());

        Attachment deleted = attachmentRepository
                .findById(created.id())
                .orElseThrow();

        assertNotNull(deleted.getDeletedAt());

        assertTrue(
                attachmentRepository
                        .findByIdAndDeletedAtIsNull(created.id())
                        .isEmpty()
        );

        assertThrows(
                ApiException.class,
                () -> attachmentService.getAttachment(created.id())
        );

        assertNotNull(storage.download(storageKey));
    }

    @Test
    void deletingNonexistentAttachmentThrowsException() {

        UUID nonexistentId = UUID.randomUUID();

        assertThrows(
                ApiException.class,
                () -> attachmentService.deleteAttachment(nonexistentId)
        );
    }
}
