package es.grupo18.urbanpulse.attachments;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.attachment.model.Attachment;
import es.grupo18.urbanpulse.attachment.repository.AttachmentRepository;
import es.grupo18.urbanpulse.attachment.service.AttachmentContent;
import es.grupo18.urbanpulse.attachment.service.AttachmentService;
import es.grupo18.urbanpulse.attachment.service.ObjectStorageService;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.shared.error.ApiException;
import jakarta.persistence.EntityManager;

@SpringBootTest
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
@Transactional
class AttachmentServicePostgresIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ObjectStorageService storage;

    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        // getAttachmentContent() reads the bytes returned by download(),
        // so the stub has to return a real JPEG for the decode assertions to work.
        when(storage.download(anyString())).thenAnswer(invocation -> createTestJpeg());

        String encodedPassword = passwordEncoder.encode("test-password");
        testUser = new User(
                "Attachment Test User",
                "attachment-test-" + UUID.randomUUID() + "@example.com",
                encodedPassword);
        entityManager.persist(testUser);
        entityManager.flush();
    }

    @AfterEach
    void cleanUp() {
        if (testUser == null) {
            return;
        }
    }

    private byte[] createTestJpeg() throws Exception {

        BufferedImage image = new BufferedImage(
                100,
                100,
                BufferedImage.TYPE_INT_RGB);

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

        assertThat(ImageIO.write(image, "jpeg", output)).isTrue();

        return output.toByteArray();
    }

    private MockMultipartFile createValidFile() throws Exception {

        return new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                createTestJpeg());
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
                testUser);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();

        assertThat(result.fileName()).isEqualTo("test-image.jpg");
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.sizeBytes()).isPositive();
        assertThat(result.description()).isEqualTo("Test photograph");

        Attachment saved = attachmentRepository
                .findById(result.id())
                .orElseThrow();

        assertThat(saved.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(saved.getOriginalFilename()).isEqualTo("test-image.jpg");

        verify(storage).upload(
                eq(saved.getStorageKey()),
                any(byte[].class),
                eq("image/jpeg"));
    }

    @Test
    void uploadWithoutDescriptionWorks() throws Exception {

        AttachmentDTO result = attachmentService.attachPhoto(
                createValidFile(),
                null,
                testUser);

        assertThat(result.id()).isNotNull();
        assertThat(result.description()).isNull();
    }

    // ---------------------------------------------------------
    // DOWNLOAD TESTS
    // ---------------------------------------------------------

    @Test
    void downloadAttachmentReturnsCorrectContent() throws Exception {

        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "Download test",
                testUser);

        AttachmentContent content = attachmentService.getAttachmentContent(created.id());

        assertThat(content).isNotNull();
        assertThat(content.data()).isNotNull();
        assertThat(content.data().length).isPositive();
        assertThat(content.fileName()).isEqualTo("test-image.jpg");
        assertThat(content.contentType()).isEqualTo("image/jpeg");
        assertThat(content.sizeBytes()).isEqualTo(content.data().length);

        BufferedImage decoded = ImageIO.read(
                new java.io.ByteArrayInputStream(content.data()));

        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(100);
        assertThat(decoded.getHeight()).isEqualTo(100);
    }

    // ---------------------------------------------------------
    // RETRIEVAL TESTS
    // ---------------------------------------------------------

    @Test
    void getAttachmentReturnsCorrectMetadata() throws Exception {

        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "Metadata test",
                testUser);

        AttachmentDTO result = attachmentService.getAttachment(created.id());

        assertThat(result.id()).isEqualTo(created.id());
        assertThat(result.fileName()).isEqualTo(created.fileName());
        assertThat(result.contentType()).isEqualTo(created.contentType());
        assertThat(result.description()).isEqualTo(created.description());
    }

    @Test
    void getAttachmentsReturnsUploadedAttachment() throws Exception {

        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "List test",
                testUser);

        List<AttachmentDTO> results = attachmentService.getAttachments();

        assertThat(results)
                .anyMatch(attachment -> attachment.id().equals(created.id()));
    }

    @Test
    void retrievingNonexistentAttachmentThrowsException() {

        UUID nonexistentId = UUID.randomUUID();

        assertThatThrownBy(() -> attachmentService.getAttachment(nonexistentId))
                .isInstanceOf(ApiException.class);
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
                new byte[0]);

        assertThatThrownBy(() -> attachmentService.attachPhoto(
                emptyFile,
                null,
                testUser))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void uploadingInvalidFormatThrowsException() {

        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "invalid.txt",
                "text/plain",
                "This is not an image".getBytes());

        assertThatThrownBy(() -> attachmentService.attachPhoto(
                invalidFile,
                null,
                testUser))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void descriptionOver300CharactersThrowsException() throws Exception {

        String description = "A".repeat(301);

        assertThatThrownBy(() -> attachmentService.attachPhoto(
                createValidFile(),
                description,
                testUser))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void nullUploaderThrowsException() throws Exception {

        assertThatThrownBy(() -> attachmentService.attachPhoto(
                createValidFile(),
                null,
                null))
                .isInstanceOf(ApiException.class);
    }

    // ---------------------------------------------------------
    // DELETE TESTS
    // ---------------------------------------------------------

    @Test
    void deletingAttachmentMarksItAsDeleted() throws Exception {

        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "Delete test",
                testUser);

        Attachment beforeDeletion = attachmentRepository
                .findById(created.id())
                .orElseThrow();

        String storageKey = beforeDeletion.getStorageKey();

        attachmentService.deleteAttachment(created.id());

        Attachment deleted = attachmentRepository
                .findById(created.id())
                .orElseThrow();

        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(attachmentRepository.findByIdAndDeletedAtIsNull(created.id())).isEmpty();
        assertThatThrownBy(() -> attachmentService.getAttachment(created.id()))
                .isInstanceOf(ApiException.class);

        // deleteAttachment() is a soft delete: it must NOT remove the blob.
        verify(storage).upload(eq(storageKey), any(byte[].class), eq("image/jpeg"));
        verify(storage, never()).delete(storageKey);
    }

    @Test
    void deletingNonexistentAttachmentThrowsException() {

        UUID nonexistentId = UUID.randomUUID();

        assertThatThrownBy(() -> attachmentService.deleteAttachment(nonexistentId))
                .isInstanceOf(ApiException.class);
    }
}
