package es.grupo18.urbanpulse.attachments;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import es.grupo18.urbanpulse.incidents.model.Coordinates;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.model.Location;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private Incident testIncident;

    @BeforeEach
    void setUp() throws Exception {
        when(storage.download(anyString())).thenAnswer(invocation -> createTestJpeg());

        String encodedPassword = passwordEncoder.encode("test-password");
        testUser = new User(
                "Attachment Test User",
                "attachment-test-" + UUID.randomUUID() + "@example.com",
                encodedPassword);
        entityManager.persist(testUser);
        entityManager.flush();

        // Incident constructor: (User reporter, String title, String description,
        // Category category, Location location, Instant now)
        // Location constructor: (Coordinates coordinates)
        // Coordinates constructor: (Double latitude, Double longitude, Double
        // accuracyMeters)
        Location location = new Location(new Coordinates(40.4168, -3.7038, 10.0));

        testIncident = new Incident(
                testUser,
                "Test incident for attachments",
                "Incident created by AttachmentServicePostgresIT",
                null,
                location,
                Instant.now());
        entityManager.persist(testIncident);
        entityManager.flush();
    }

    @AfterEach
    void cleanUp() {
        // kept for parity with the previous version
    }

    private byte[] createTestJpeg() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
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
                "file", "test-image.jpg", "image/jpeg", createTestJpeg());
    }

    // ---------------- UPLOAD ----------------

    @Test
    void uploadValidJpegCreatesAttachment() throws Exception {
        AttachmentDTO result = attachmentService.attachPhoto(
                createValidFile(),
                "Test photograph",
                testUser,
                testIncident.getId());

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.fileName()).isEqualTo("test-image.jpg");
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.sizeBytes()).isPositive();
        assertThat(result.description()).isEqualTo("Test photograph");
        assertThat(result.incidentId()).isEqualTo(testIncident.getId());

        Attachment saved = attachmentRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(saved.getOriginalFilename()).isEqualTo("test-image.jpg");
        assertThat(saved.getIncidentId()).isEqualTo(testIncident.getId());

        verify(storage).upload(eq(saved.getStorageKey()), any(byte[].class), eq("image/jpeg"));
    }

    @Test
    void uploadWithoutDescriptionWorks() throws Exception {
        AttachmentDTO result = attachmentService.attachPhoto(
                createValidFile(),
                null,
                testUser,
                testIncident.getId());
        assertThat(result.id()).isNotNull();
        assertThat(result.description()).isNull();
    }

    // ---------------- DOWNLOAD ----------------

    @Test
    void downloadAttachmentReturnsCorrectContent() throws Exception {
        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(),
                "Download test",
                testUser,
                testIncident.getId());

        AttachmentContent content = attachmentService.getAttachmentContent(created.id());

        assertThat(content).isNotNull();
        assertThat(content.data()).isNotNull();
        assertThat(content.data().length).isPositive();
        assertThat(content.fileName()).isEqualTo("test-image.jpg");
        assertThat(content.contentType()).isEqualTo("image/jpeg");
        assertThat(content.sizeBytes()).isEqualTo(content.data().length);

        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(content.data()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(100);
        assertThat(decoded.getHeight()).isEqualTo(100);
    }

    // ---------------- RETRIEVAL ----------------

    @Test
    void getAttachmentReturnsCorrectMetadata() throws Exception {
        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(), "Metadata test", testUser, testIncident.getId());

        AttachmentDTO result = attachmentService.getAttachment(created.id());

        assertThat(result.id()).isEqualTo(created.id());
        assertThat(result.fileName()).isEqualTo(created.fileName());
        assertThat(result.contentType()).isEqualTo(created.contentType());
        assertThat(result.description()).isEqualTo(created.description());
    }

    @Test
    void getAttachmentsReturnsUploadedAttachment() throws Exception {
        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(), "List test", testUser, testIncident.getId());

        List<AttachmentDTO> results = attachmentService.getAttachments();

        assertThat(results).anyMatch(a -> a.id().equals(created.id()));
    }

    @Test
    void retrievingNonexistentAttachmentThrowsException() {
        assertThatThrownBy(() -> attachmentService.getAttachment(UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }

    // ---------------- VALIDATION ----------------

    @Test
    void uploadingEmptyFileThrowsException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> attachmentService.attachPhoto(
                emptyFile, null, testUser, testIncident.getId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void uploadingInvalidFormatThrowsException() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "invalid.txt", "text/plain", "This is not an image".getBytes());

        assertThatThrownBy(() -> attachmentService.attachPhoto(
                invalidFile, null, testUser, testIncident.getId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void descriptionOver300CharactersThrowsException() throws Exception {
        assertThatThrownBy(() -> attachmentService.attachPhoto(
                createValidFile(), "A".repeat(301), testUser, testIncident.getId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void nullUploaderThrowsException() throws Exception {
        assertThatThrownBy(() -> attachmentService.attachPhoto(
                createValidFile(), null, null, testIncident.getId()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void nullIncidentIdThrowsException() throws Exception {
        assertThatThrownBy(() -> attachmentService.attachPhoto(
                createValidFile(), null, testUser, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void unknownIncidentIdThrowsException() throws Exception {
        assertThatThrownBy(() -> attachmentService.attachPhoto(
                createValidFile(), null, testUser, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }

    // ---------------- DELETE ----------------

    @Test
    void deletingAttachmentMarksItAsDeleted() throws Exception {
        AttachmentDTO created = attachmentService.attachPhoto(
                createValidFile(), "Delete test", testUser, testIncident.getId());

        Attachment beforeDeletion = attachmentRepository.findById(created.id()).orElseThrow();
        String storageKey = beforeDeletion.getStorageKey();

        attachmentService.deleteAttachment(created.id());

        Attachment deleted = attachmentRepository.findById(created.id()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(attachmentRepository.findByIdAndDeletedAtIsNull(created.id())).isEmpty();
        assertThatThrownBy(() -> attachmentService.getAttachment(created.id()))
                .isInstanceOf(ApiException.class);

        verify(storage).upload(eq(storageKey), any(byte[].class), eq("image/jpeg"));
        verify(storage, never()).delete(storageKey);
    }

    @Test
    void deletingNonexistentAttachmentThrowsException() {
        assertThatThrownBy(() -> attachmentService.deleteAttachment(UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }
}
