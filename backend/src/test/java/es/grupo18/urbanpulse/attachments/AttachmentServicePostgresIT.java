package es.grupo18.urbanpulse.attachments;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.attachment.service.AttachmentService;
import es.grupo18.urbanpulse.attachment.service.ImageSanitizer;
import es.grupo18.urbanpulse.attachment.service.ObjectStorageService;
import es.grupo18.urbanpulse.auth.dto.RegisterRequest;
import es.grupo18.urbanpulse.auth.dto.TokenDTO;
import es.grupo18.urbanpulse.auth.service.AuthService;
import es.grupo18.urbanpulse.incidents.dto.CoordinatesDTO;
import es.grupo18.urbanpulse.incidents.dto.CreateIncidentRequest;
import es.grupo18.urbanpulse.incidents.model.IncidentStatus;
import es.grupo18.urbanpulse.incidents.service.IncidentService;
import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.shared.mail.EmailSender;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP con JWT firmado, PostgreSQL real y almacenamiento aislado en memoria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(printOnlyOnFailure = false, print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@TestPropertySource("classpath:auth-test.properties")
@Testcontainers
class AttachmentServicePostgresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    AttachmentService service;
    @Autowired
    IncidentService incidents;
    @Autowired
    AuthService auth;
    @Autowired
    es.grupo18.urbanpulse.auth.service.EmailVerificationCleanup cleanup;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @Autowired
    JwtEncoder encoder;
    @Value("${local.server.port}")
    int port;
    @MockitoBean
    ObjectStorageService storage;
    @MockitoBean
    EmailSender emailSender;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private TokenDTO owner;
    private UUID incident;
    private byte[] jpeg;

    @BeforeEach
    void isolate() throws Exception {
        jdbc.execute("truncate attachments,status_changes,incident_reviews,priority_decisions,incidents,audit_events,"
                + "staff_invitations,department_memberships,email_verifications,user_roles,users,categories,departments");
        objects.clear();
        jpeg = image("jpeg");
        doAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            objects.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(storage).upload(anyString(), any(byte[].class), anyString());
        when(storage.download(anyString())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return objects.get(i.getArgument(0));
        });
        doAnswer(i -> {
            objects.remove(i.getArgument(0));
            return null;
        }).when(storage).delete(anyString());
        owner = account(null);
        incident = createIncident(owner);
    }

    @Test
    void fiveHttpOperationsExposeExactDtoHeadersAndRetainDeletedObject() throws Exception {
        var uploaded = mvc.perform(multipart(path()).file(file("../foto.jpeg", "text/plain", jpeg))
                .header("Authorization", bearer(owner))).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        var node = json.readTree(uploaded.getContentAsString());
        assertThat(node.propertyNames()).containsExactlyInAnyOrder("id", "incidentId", "originalFilename",
                "contentType",
                "sizeBytes", "uploadedBy", "createdAt", "description");
        assertThat(node.get("description").isNull()).isTrue();
        assertThat(node.get("originalFilename").asString()).isEqualTo("foto.jpeg");
        assertThat(node.get("uploadedBy").asString()).isEqualTo(owner.user().id().toString());
        assertThat(node.get("contentType").asString()).isEqualTo("image/jpeg");
        UUID id = UUID.fromString(node.get("id").asString());
        assertThat(id.version()).isEqualTo(7);
        String location = path() + "/" + id;
        assertThat(uploaded.getHeader("Location")).isEqualTo(location);
        mvc.perform(get(location).header("Authorization", bearer(owner))).andExpect(status().isOk())
                .andExpect(content().json(uploaded.getContentAsString()))
                .andExpect(header().string("Cache-Control", "no-store"));
        var binary = mvc.perform(get(location + "/content").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        assertThat(binary.getHeader("Content-Disposition")).contains("attachment;", "foto.jpeg");
        assertThat(Long.parseLong(binary.getHeader("Content-Length"))).isEqualTo(node.get("sizeBytes").asLong());
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(binary.getContentAsByteArray()))).isNotNull();
        mvc.perform(get(path()).header("Authorization", bearer(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete(location).header("Authorization", bearer(owner))).andExpect(status().isNoContent())
                .andExpect(content().string("")).andExpect(header().string("Cache-Control", "no-store"));
        Instant deleted = jdbc
                .queryForObject("select deleted_at from attachments where id=?", java.sql.Timestamp.class, id)
                .toInstant();
        for (String suffix : List.of("", "/content")) {
            mvc.perform(get(location + suffix).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        }
        mvc.perform(delete(location).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select deleted_at from attachments where id=?", java.sql.Timestamp.class, id)
                .toInstant()).isEqualTo(deleted);
        mvc.perform(get(path()).header("Authorization", bearer(owner))).andExpect(jsonPath("$.length()").value(0));
        assertThat(objects).hasSize(1);
        verify(storage, never()).delete(anyString());
        assertThat(jdbc.queryForList(
                "select action from audit_events where resource_type='ATTACHMENT' order by occurred_at", String.class))
                .containsExactly("ATTACHMENT_CREATED", "ATTACHMENT_DELETED");
        assertThat(incidents.history(owner.user().id(), 0, incident, 1, 20).pagination().totalItems()).isEqualTo(1);
    }

    @Test
    void foreignAndMissingIncidentsAreIndistinguishableForEveryOperation() throws Exception {
        var created = upload(owner);
        var stranger = account(null);
        for (UUID parent : List.of(incident, UUID.randomUUID())) {
            String path = "/api/v1/incidents/" + parent + "/attachments";
            for (String suffix : List.of("", "/" + created.id(), "/" + created.id() + "/content")) {
                mvc.perform(get(path + suffix).header("Authorization", bearer(stranger)))
                        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));
            }
            mvc.perform(
                    multipart(path).file(file("a.jpg", "image/jpeg", jpeg)).header("Authorization", bearer(stranger)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));
            mvc.perform(delete(path + "/" + created.id()).header("Authorization", bearer(stranger)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));
        }
        UUID otherOwn = createIncident(owner);
        String wrong = "/api/v1/incidents/" + otherOwn + "/attachments/" + created.id();
        mvc.perform(get(wrong).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        mvc.perform(get(wrong + "/content").header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        mvc.perform(delete(wrong).header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        verify(storage, never()).download(anyString());
    }

    @Test
    void globalStaffAndReporterHaveSameOperationsWhileDepartmentRolesDoNotGrantAccess() throws Exception {
        for (String role : List.of("OPERATOR", "ADMIN")) {
            var staff = account(role);
            var dto = upload(staff);
            assertThat(service.getAttachment(staff.user().id(), 0, incident, dto.id())).isEqualTo(dto);
            assertThat(service.getAttachments(staff.user().id(), 0, incident)).hasSize(1);
            assertThat(service.getAttachmentContent(staff.user().id(), 0, incident, dto.id()).data()).isNotEmpty();
            // Reporter can delete a photo uploaded by staff.
            service.deleteAttachment(owner.user().id(), 0, incident, dto.id());
            var own = upload(owner);
            mvc.perform(delete(path() + "/" + own.id()).header("Authorization", bearer(staff)))
                    .andExpect(status().isNoContent());
        }
        UUID department = jdbc.queryForObject("insert into departments(name,code) values ('Equipo','EQ') returning id",
                UUID.class);
        for (String role : List.of("TECHNICIAN", "DEPARTMENT_COORDINATOR")) {
            var technician = account(null);
            jdbc.update(
                    "insert into department_memberships(id,user_id,department_id,role_code,joined_at) values(uuidv7(),?,?,?,now())",
                    technician.user().id(), department, role);
            mvc.perform(get(path()).header("Authorization", bearer(technician))).andExpect(status().isNotFound());
            UUID own = createIncident(technician);
            mvc.perform(multipart("/api/v1/incidents/" + own + "/attachments").file(file("a.jpg", "image/jpeg", jpeg))
                    .header("Authorization", bearer(technician))).andExpect(status().isCreated());
        }
    }

    @Test
    void jwtMustBeSignedCurrentVerifiedAndActiveAndNoGlobalRoutesExist() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("urbanpulse-tests").subject(owner.user().id().toString()).claim("ver", 0)
                        .issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600))
                        .build()))
                .getTokenValue();
        mvc.perform(get(path()).header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        jdbc.update("update users set email_verified=false where id=?", owner.user().id());
        mvc.perform(get(path()).header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        assertStatus(() -> service.getAttachments(owner.user().id(), 0, incident), 403);
        jdbc.update("update users set email_verified=true,token_version=1 where id=?", owner.user().id());
        mvc.perform(get(path()).header("Authorization", bearer(owner))).andExpect(status().isUnauthorized());
        assertStatus(() -> service.getAttachments(owner.user().id(), 0, incident), 401);
        jdbc.update("update users set token_version=0,active=false where id=?", owner.user().id());
        mvc.perform(get(path()).header("Authorization", bearer(owner))).andExpect(status().isUnauthorized());
        jdbc.update("update users set active=true where id=?", owner.user().id());
        mvc.perform(get("/api/v1/attachments").header("Authorization", bearer(owner))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/attachments").file(file("a.jpg", "image/jpeg", jpeg)).header("Authorization",
                bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void allExistingIncidentStatesAllowUploadAndDeleteWithoutChangingState() throws Exception {
        for (IncidentStatus state : IncidentStatus.values()) {
            jdbc.update("update incidents set status=? where id=?", state.name(), incident);
            var dto = upload(owner);
            service.deleteAttachment(owner.user().id(), 0, incident, dto.id());
            assertThat(jdbc.queryForObject("select status from incidents where id=?", String.class, incident))
                    .isEqualTo(state.name());
        }
    }

    @Test
    void fiveAcrossUploadersStableOrderAndSoftDeleteReleasesSlot() throws Exception {
        assertThat(service.getAttachments(owner.user().id(), 0, incident)).isEmpty();
        var operator = account("OPERATOR");
        List<AttachmentDTO> created = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            created.add(upload(i % 2 == 0 ? owner : operator));
        jdbc.update("update attachments set created_at='2026-11-11T10:00:00Z' where incident_id=?", incident);
        assertThat(service.getAttachments(owner.user().id(), 0, incident).stream().map(a -> a.id().toString()).toList())
                .isSortedAccordingTo(Comparator.reverseOrder());
        mvc.perform(
                multipart(path()).file(file("six.jpg", "image/jpeg", jpeg)).header("Authorization", bearer(operator)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTACHMENT_LIMIT_REACHED"));
        assertThat(objects).hasSize(5);
        assertThat(count()).isEqualTo(5);
        service.deleteAttachment(operator.user().id(), 0, incident, created.getFirst().id());
        upload(owner);
        assertThat(count()).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from attachments", Integer.class)).isEqualTo(6);
    }

    @Test
    void concurrentUploadsWithOneAndZeroSlotsNeverExceedFiveAndCompensate() throws Exception {
        var operator = account("OPERATOR");
        for (int i = 0; i < 4; i++)
            upload(owner);
        for (int remaining : List.of(1, 0)) {
            CyclicBarrier barrier = new CyclicBarrier(2);
            doAnswer(i -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                objects.put(i.getArgument(0), i.getArgument(1));
                barrier.await(20, TimeUnit.SECONDS);
                return null;
            }).when(storage).upload(anyString(), any(byte[].class), anyString());
            try (var executor = Executors.newFixedThreadPool(2)) {
                var futures = List.of(owner, operator).stream().map(actor -> executor.submit(() -> {
                    try {
                        upload(actor);
                        return 201;
                    } catch (ApiException e) {
                        return e.status().value();
                    }
                })).toList();
                assertThat(List.of(futures.get(0).get(40, TimeUnit.SECONDS), futures.get(1).get(40, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrderElementsOf(remaining == 1 ? List.of(201, 409) : List.of(409, 409));
            }
            assertThat(count()).isEqualTo(5);
            assertThat(objects).hasSize(5);
        }
        verify(storage, times(3)).delete(anyString());
    }

    @Test
    void sqlConstraintsRejectOrphansFormatsSizesDatesAndUnsafeNames() throws Exception {
        var dto = upload(owner);
        for (String change : List.of("incident_id=null", "uploaded_by=null",
                "incident_id='00000000-0000-0000-0000-000000000000'",
                "uploaded_by='00000000-0000-0000-0000-000000000000'", "content_type='IMAGE_WEBP'", "size_bytes=0",
                "size_bytes=10485761",
                "deleted_at=created_at-interval '1 second'", "original_filename='../a.jpg'",
                "original_filename=E'a\\nb.jpg'",
                "description=repeat('x',301)")) {
            assertThatThrownBy(() -> jdbc.update("update attachments set " + change + " where id=?", dto.id()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("delete from incidents where id=?", incident))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success", String.class))
                .containsExactly("1");
        assertThat(jdbc.queryForObject("select incident_id from attachments where id=?", UUID.class, dto.id()))
                .isEqualTo(incident);
    }

    @Test
    void putAndGetFailuresAreSanitizedAndDoNotInsertMetadata() throws Exception {
        doThrow(new IllegalStateException("secret-key SQL AWS token")).when(storage).upload(anyString(),
                any(byte[].class), anyString());
        var response = mvc
                .perform(multipart(path()).file(file("a.jpg", "image/jpeg", jpeg)).header("Authorization",
                        bearer(owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_STORAGE_ERROR"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret-key", "AWS", "SQL", "storageKey");
        assertThat(count()).isZero();
        doAnswer(i -> {
            objects.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(storage).upload(anyString(), any(byte[].class), anyString());
        var dto = upload(owner);
        when(storage.download(anyString())).thenThrow(new IllegalStateException("private-key"));
        mvc.perform(get(path() + "/" + dto.id() + "/content").header("Authorization", bearer(owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_DOWNLOAD_ERROR"));
    }

    @Test
    void commitFailureCompensatesAndCleanupFailurePreservesPrimaryCause() throws Exception {
        jdbc.execute(
                "create function fail_attachment_commit() returns trigger language plpgsql as $$ begin raise exception 'synthetic commit failure'; end $$");
        jdbc.execute(
                "create constraint trigger fail_attachment_commit after insert on attachments deferrable initially deferred for each row execute function fail_attachment_commit()");
        try {
            assertThatThrownBy(() -> upload(owner)).hasRootCauseMessage(
                    "ERROR: synthetic commit failure\n  Where: PL/pgSQL function fail_attachment_commit() line 1 at RAISE");
            assertThat(objects).isEmpty();
            assertThat(count()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from audit_events where resource_type='ATTACHMENT'",
                    Integer.class)).isZero();
            doThrow(new IllegalStateException("synthetic cleanup failure")).when(storage).delete(anyString());
            assertThatThrownBy(() -> upload(owner)).satisfies(e -> {
                assertThat(e.getSuppressed()).hasSize(1);
                assertThat(e.getSuppressed()[0]).hasMessage("synthetic cleanup failure");
            });
            assertThat(count()).isZero();
        } finally {
            jdbc.execute("drop trigger fail_attachment_commit on attachments");
            jdbc.execute("drop function fail_attachment_commit()");
        }
    }

    @Test
    void authorizationIsRevalidatedAfterPutAndInvalidatedUploadIsCompensated() throws Exception {
        doAnswer(i -> {
            objects.put(i.getArgument(0), i.getArgument(1));
            jdbc.update("update users set token_version=1 where id=?", owner.user().id());
            return null;
        }).when(storage).upload(anyString(), any(byte[].class), anyString());
        assertStatus(() -> upload(owner), 401);
        assertThat(objects).isEmpty();
        assertThat(count()).isZero();
    }

    @Test
    void invalidMultipartAndImageDataProduceClientErrors() throws Exception {
        mvc.perform(multipart(path()).header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        for (byte[] data : List.of(new byte[0], "GIF89a".getBytes(), "RIFFxxxxWEBP".getBytes(), "%PDF".getBytes(),
                new byte[] { (byte) 255, (byte) 216, (byte) 255, 0 }, Arrays.copyOf(jpeg, jpeg.length - 20))) {
            mvc.perform(
                    multipart(path()).file(file("a.jpg", "image/jpeg", data)).header("Authorization", bearer(owner)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INVALID_ATTACHMENT_FORMAT"));
        }
        mvc.perform(multipart(path()).file(file("a.jpg", "image/jpeg", new byte[ImageSanitizer.MAX_BYTES + 1]))
                .header("Authorization", bearer(owner))).andExpect(status().isContentTooLarge());
        mvc.perform(multipart(path()).file(file("a.jpg", "image/jpeg", jpeg)).param("description", "🚦".repeat(301))
                .header("Authorization", bearer(owner))).andExpect(status().isUnprocessableContent());
        var dto = service.attachPhoto(owner.user().id(), 0, incident,
                file("C:\\fakepath\\\r\nfoto.png", "application/pdf", image("png")), "🚦".repeat(300));
        assertThat(dto.originalFilename()).isEqualTo("foto.png");
        assertThat(dto.contentType()).isEqualTo("image/png");
        assertThat(dto.description()).isEqualTo("🚦".repeat(300));
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void servletRejectsOversizedMultipartBeforeControllerWithStable413() throws Exception {
        String boundary = "urbanpulse-boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"large.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[ImageSanitizer.MAX_BYTES + 1]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path()))
                    .header("Authorization", bearer(owner))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("ATTACHMENT_TOO_LARGE");
        }
        verifyNoInteractions(storage);
    }

    @Test
    void readFailureAndFalseDeclaredSizeCannotReachStorage() throws Exception {
        var unreadable = mock(org.springframework.web.multipart.MultipartFile.class);
        when(unreadable.getInputStream()).thenThrow(new IOException("synthetic private filename"));
        assertStatus(() -> service.attachPhoto(owner.user().id(), 0, incident, unreadable, null), 400);
        var lying = mock(org.springframework.web.multipart.MultipartFile.class);
        when(lying.getSize()).thenReturn(1L);
        when(lying.getInputStream())
                .thenReturn(new java.io.ByteArrayInputStream(new byte[ImageSanitizer.MAX_BYTES + 1]));
        assertStatus(() -> service.attachPhoto(owner.user().id(), 0, incident, lying, null), 413);
        verifyNoInteractions(storage);
        assertThat(count()).isZero();
    }

    @Test
    void auditFailureRollsBackCreationAndDeletionTogetherWithMetadata() throws Exception {
        jdbc.execute(
                "alter table audit_events add constraint reject_attachment_audit check (resource_type <> 'ATTACHMENT') not valid");
        try {
            assertThatThrownBy(() -> upload(owner)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(count()).isZero();
            assertThat(objects).isEmpty();
        } finally {
            jdbc.execute("alter table audit_events drop constraint reject_attachment_audit");
        }
        var dto = upload(owner);
        jdbc.execute(
                "alter table audit_events add constraint reject_attachment_delete check (action <> 'ATTACHMENT_DELETED') not valid");
        try {
            assertThatThrownBy(() -> service.deleteAttachment(owner.user().id(), 0, incident, dto.id()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(service.getAttachment(owner.user().id(), 0, incident, dto.id())).isEqualTo(dto);
            assertThat(objects).hasSize(1);
        } finally {
            jdbc.execute("alter table audit_events drop constraint reject_attachment_delete");
        }
    }

    @Test
    void cleanupPreservesUploaderReferencedOnlyByDeletedEvidence() throws Exception {
        var operator = account("OPERATOR");
        var dto = upload(operator);
        service.deleteAttachment(owner.user().id(), 0, incident, dto.id());
        jdbc.update("delete from audit_events where actor_user_id=?", operator.user().id());
        jdbc.update("update users set email_verified=false,created_at=now()-interval '2 days' where id=?",
                operator.user().id());
        cleanup.clean();
        assertThat(jdbc.queryForObject("select count(*) from users where id=?", Integer.class, operator.user().id()))
                .isEqualTo(1);
    }

    private AttachmentDTO upload(TokenDTO actor) {
        return service.attachPhoto(actor.user().id(), 0, incident, file("a.jpg", "image/jpeg", jpeg), null);
    }

    private TokenDTO account(String role) {
        var token = auth
                .register(new RegisterRequest("Usuario sintético", UUID.randomUUID() + "@example.org", "password-123"));
        jdbc.update("update users set email_verified=true where id=?", token.user().id());
        if (role != null)
            jdbc.update("insert into user_roles values (?,?)", token.user().id(), role);
        return token;
    }

    private UUID createIncident(TokenDTO actor) {
        return incidents.create(actor.user().id(), 0, new CreateIncidentRequest("Bache grande", "Pavimento deteriorado",
                new CoordinatesDTO(36.7, -4.4, null), null)).id();
    }

    private String path() {
        return "/api/v1/incidents/" + incident + "/attachments";
    }

    private static String bearer(TokenDTO token) {
        return "Bearer " + token.accessToken();
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from attachments where deleted_at is null", Integer.class);
    }

    private static MockMultipartFile file(String name, String mime, byte[] data) {
        return new MockMultipartFile("file", name, mime, data);
    }

    private static byte[] image(String format) throws IOException {
        var image = new BufferedImage(32, 24,
                format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        image.setRGB(2, 2, 0x80123456);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private static void assertStatus(Runnable action, int expected) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(expected));
    }
}
