package es.grupo18.urbanpulse.attachment.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.attachment.mappers.AttachmentMapper;
import es.grupo18.urbanpulse.attachment.model.Attachment;
import es.grupo18.urbanpulse.attachment.model.ContentType;
import es.grupo18.urbanpulse.attachment.repository.AttachmentRepository;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.repository.IncidentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final int MAX_FILENAME_LENGTH = 128;

    private final AttachmentRepository attachments;
    private final IncidentRepository incidents;
    private final ObjectStorageService storage;
    private final ImageSanitizer imageSanitizer;
    private final AttachmentMapper mapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * Uploads an attachment to Neon Object Storage
     * and saves its metadata in PostgreSQL, linked to the given incident.
     */
    public AttachmentDTO attachPhoto(
            MultipartFile file,
            String description,
            User uploader,
            UUID incidentId) {

        validateFile(file);
        validateDescription(description);

        if (uploader == null || !uploader.isActive()) {
            throw ApiException.unauthorized();
        }

        if (incidentId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INCIDENT_REQUIRED",
                    "Debe indicar la incidencia asociada a la fotografía.");
        }

        Incident incident = incidents.findById(incidentId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "INCIDENT_NOT_FOUND",
                        "No se encontró la incidencia solicitada."));

        String storageKey = UUID.randomUUID().toString();

        try {
            byte[] originalBytes = file.getBytes();

            ContentType contentType = detectContentType(originalBytes);

            byte[] sanitizedBytes = imageSanitizer.sanitize(
                    originalBytes,
                    contentType);

            if (sanitizedBytes == null || sanitizedBytes.length == 0) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "INVALID_ATTACHMENT",
                        "No se pudo procesar la fotografía.");
            }

            String filename = sanitizeFilename(
                    file.getOriginalFilename());

            // Upload image to Neon Object Storage.
            try {
                storage.upload(
                        storageKey,
                        sanitizedBytes,
                        contentType.getMimeType());
            } catch (RuntimeException exception) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ATTACHMENT_STORAGE_ERROR",
                        "No se pudo almacenar la fotografía.");
            }

            // Save metadata to PostgreSQL.
            try {
                return transactionTemplate.execute(status -> {

                    Attachment attachment = Attachment.create(
                            incident,
                            uploader,
                            filename,
                            contentType,
                            storageKey,
                            sanitizedBytes.length,
                            description);

                    Attachment saved = attachments.saveAndFlush(attachment);

                    return mapper.toDTO(saved);
                });

            } catch (RuntimeException exception) {

                // Compensate if database persistence fails.
                try {
                    storage.delete(storageKey);
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }

                throw exception;
            }

        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ATTACHMENT_PROCESSING_ERROR",
                    "No se pudo procesar la fotografía.");
        }
    }

    public List<AttachmentDTO> getAttachments() {
        return attachments.findAllByDeletedAtIsNull()
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    public AttachmentDTO getAttachment(UUID attachmentId) {
        Attachment attachment = findActiveAttachment(attachmentId);
        return mapper.toDTO(attachment);
    }

    public AttachmentContent getAttachmentContent(UUID attachmentId) {
        Attachment attachment = findActiveAttachment(attachmentId);

        byte[] data;

        try {
            data = storage.download(attachment.getStorageKey());
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ATTACHMENT_DOWNLOAD_ERROR",
                    "No se pudo recuperar la fotografía.");
        }

        return new AttachmentContent(
                attachment.getOriginalFilename(),
                attachment.getContentType().getMimeType(),
                data.length,
                data);
    }

    private Attachment findActiveAttachment(UUID attachmentId) {
        return attachments.findByIdAndDeletedAtIsNull(attachmentId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ATTACHMENT_NOT_FOUND",
                        "No se encontró la evidencia solicitada."));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_ATTACHMENT",
                    "Debe proporcionar una fotografía.");
        }
    }

    private void validateDescription(String description) {
        if (description != null &&
                description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "INVALID_ATTACHMENT_DESCRIPTION",
                    "La descripción no puede superar los 300 caracteres.");
        }
    }

    public void deleteAttachment(UUID attachmentId) {
        Attachment attachment = findActiveAttachment(attachmentId);
        attachment.markAsDeleted();
        attachments.save(attachment);
    }

    private ContentType detectContentType(byte[] bytes) {
        if (bytes == null) {
            throw invalidFormat();
        }

        if (bytes.length >= 3 &&
                (bytes[0] & 0xFF) == 0xFF &&
                (bytes[1] & 0xFF) == 0xD8 &&
                (bytes[2] & 0xFF) == 0xFF) {
            return ContentType.IMAGE_JPEG;
        }

        if (bytes.length >= 8 &&
                (bytes[0] & 0xFF) == 0x89 &&
                bytes[1] == 0x50 &&
                bytes[2] == 0x4E &&
                bytes[3] == 0x47 &&
                bytes[4] == 0x0D &&
                bytes[5] == 0x0A &&
                bytes[6] == 0x1A &&
                bytes[7] == 0x0A) {
            return ContentType.IMAGE_PNG;
        }

        if (bytes.length >= 12 &&
                bytes[0] == 'R' &&
                bytes[1] == 'I' &&
                bytes[2] == 'F' &&
                bytes[3] == 'F' &&
                bytes[8] == 'W' &&
                bytes[9] == 'E' &&
                bytes[10] == 'B' &&
                bytes[11] == 'P') {
            return ContentType.IMAGE_WEBP;
        }

        throw invalidFormat();
    }

    private static ApiException invalidFormat() {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "INVALID_ATTACHMENT_FORMAT",
                "Solo se permiten fotografías JPEG, PNG o WebP.");
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment";
        }

        String sanitized = filename.replace('\\', '/');
        sanitized = sanitized.substring(sanitized.lastIndexOf('/') + 1);

        if (sanitized.isBlank()) {
            return "attachment";
        }

        if (sanitized.length() > MAX_FILENAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_FILENAME_LENGTH);
        }

        return sanitized;
    }
}
