package es.grupo18.urbanpulse.attachment.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.attachment.mappers.AttachmentMapper;
import es.grupo18.urbanpulse.attachment.model.Attachment;
import es.grupo18.urbanpulse.attachment.model.ContentType;
import es.grupo18.urbanpulse.attachment.repository.AttachmentRepository;
import es.grupo18.urbanpulse.audit.model.AuditEvent;
import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.incidents.service.IncidentAccess;
import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Evidencias privadas: autorización por incidencia, límite coordinado en SQL y
 * compensación S3.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final IncidentAccess access;
    private final ObjectStorageService storage;
    private final ImageSanitizer imageSanitizer;
    private final AttachmentMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final AuditEventRepository audit;

    /**
     * Sanea y sube fuera de SQL; revalida y bloquea usuario → incidencia para
     * confirmar.
     * Suspende cualquier transacción del llamador para capturar también fallos de
     * commit.
     *
     * @param actor       sujeto JWT
     * @param version     versión JWT
     * @param incidentId  incidencia visible, en cualquier estado
     * @param file        fotografía obligatoria, máximo 10 MiB
     * @param description texto nullable, máximo 300 puntos Unicode
     * @return metadatos confirmados, sin clave S3
     * @throws ApiException 401/403/404 por acceso, 409 sin plaza, 413 por tamaño,
     *                      422 por imagen/texto inválido o 503 por almacenamiento
     *                      no disponible
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AttachmentDTO attachPhoto(UUID actor, long version, UUID incidentId, MultipartFile file,
            String description) {
        transactionTemplate
                .executeWithoutResult(tx -> access.visible(access.account(actor, version, false), incidentId, false));
        if (file == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_ATTACHMENT", "Debe proporcionar una fotografía.");
        if (file.getSize() > ImageSanitizer.MAX_BYTES)
            throw ImageSanitizer.tooLarge();
        if (description != null && (description.codePointCount(0, description.length()) > 300
                || description.codePoints().anyMatch(c -> c == 0 || Character.getType(c) == Character.SURROGATE))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ATTACHMENT_DESCRIPTION",
                    "La descripción debe contener como máximo 300 caracteres válidos.");
        }
        byte[] original;
        try (var input = file.getInputStream()) {
            original = input.readNBytes(ImageSanitizer.MAX_BYTES + 1);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_READ_ERROR", "No se pudo leer la fotografía.");
        }
        if (original.length > ImageSanitizer.MAX_BYTES)
            throw ImageSanitizer.tooLarge();
        ContentType type = imageSanitizer.detectContentType(original);
        byte[] sanitized = imageSanitizer.sanitize(original, type);
        String filename = sanitizeFilename(file.getOriginalFilename());
        String key = UUID.randomUUID().toString();
        try {
            storage.upload(key, sanitized, type.getMimeType());
        } catch (RuntimeException e) {
            throw storageFailure("ATTACHMENT_STORAGE_ERROR");
        }
        try {
            return transactionTemplate.execute(tx -> {
                User user = access.account(actor, version, true);
                var incident = access.visible(user, incidentId, true);
                if (attachments.countByIncident_IdAndDeletedAtIsNull(incidentId) >= 5) {
                    throw new ApiException(HttpStatus.CONFLICT, "ATTACHMENT_LIMIT_REACHED",
                            "La incidencia ya tiene cinco evidencias activas.");
                }
                Attachment saved = attachments.saveAndFlush(Attachment.create(incident, user, filename, type,
                        key, sanitized.length, description));
                record(user, saved, "ATTACHMENT_CREATED");
                return mapper.toDTO(saved);
            });
        } catch (RuntimeException failure) {
            try {
                storage.delete(key);
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
                // Clave opaca solo en registro interno de reconciliación; nunca en JSON
                // público.
                log.error("event=ATTACHMENT_COMPENSATION_FAILED objectKey={} primaryType={} cleanupType={}",
                        key, failure.getClass().getName(), cleanup.getClass().getName());
            }
            throw failure;
        }
    }

    /**
     * Lista solo evidencias activas después de revalidar acceso al padre.
     *
     * @param actor      sujeto JWT
     * @param version    versión JWT
     * @param incidentId incidencia visible
     * @return hasta cinco metadatos activos, createdAt DESC e id DESC
     * @throws ApiException 401/403/404 según cuenta y visibilidad
     */
    @Transactional(readOnly = true)
    public List<AttachmentDTO> getAttachments(UUID actor, long version, UUID incidentId) {
        access.visible(access.account(actor, version, false), incidentId, false);
        return attachments.findByIncident_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(incidentId)
                .stream().map(mapper::toDTO).toList();
    }

    /**
     * Consulta metadatos sin revelar evidencias ajenas, eliminadas o de otro padre.
     *
     * @param actor        sujeto JWT
     * @param version      versión JWT
     * @param incidentId   incidencia visible
     * @param attachmentId evidencia de esa incidencia
     * @return metadatos activos
     * @throws ApiException 401/403 por cuenta, 404 por acceso, pareja incorrecta o
     *                      baja
     */
    @Transactional(readOnly = true)
    public AttachmentDTO getAttachment(UUID actor, long version, UUID incidentId, UUID attachmentId) {
        access.visible(access.account(actor, version, false), incidentId, false);
        return mapper.toDTO(findActive(incidentId, attachmentId));
    }

    /**
     * Autoriza y proyecta dentro de SQL antes de descargar, sin transacción durante
     * S3.
     *
     * @param actor        sujeto JWT
     * @param version      versión JWT
     * @param incidentId   incidencia visible
     * @param attachmentId evidencia activa de esa incidencia
     * @return binario acotado y cabeceras confiables de metadatos
     * @throws ApiException 401/403/404 por acceso; 503 por fallo/inconsistencia de
     *                      almacenamiento
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AttachmentContent getAttachmentContent(UUID actor, long version, UUID incidentId, UUID attachmentId) {
        var download = transactionTemplate.execute(tx -> {
            access.visible(access.account(actor, version, false), incidentId, false);
            var attachment = findActive(incidentId, attachmentId);
            return new Download(attachment.getStorageKey(), mapper.toDTO(attachment));
        });
        try {
            byte[] data = storage.download(download.key());
            if (data.length != download.metadata().sizeBytes() || data.length > ImageSanitizer.MAX_BYTES) {
                throw storageFailure("ATTACHMENT_DOWNLOAD_ERROR");
            }
            return new AttachmentContent(download.metadata().originalFilename(), download.metadata().contentType(),
                    data.length, data);
        } catch (RuntimeException e) {
            throw storageFailure("ATTACHMENT_DOWNLOAD_ERROR");
        }
    }

    /**
     * Baja lógica y auditoría atómicas; conserva el objeto, las relaciones y el
     * estado de incidencia.
     *
     * @param actor        sujeto JWT
     * @param version      versión JWT
     * @param incidentId   incidencia visible
     * @param attachmentId evidencia activa de esa incidencia
     * @throws ApiException 401/403/404 por acceso; repetir una baja devuelve 404
     */
    @Transactional
    public void deleteAttachment(UUID actor, long version, UUID incidentId, UUID attachmentId) {
        User user = access.account(actor, version, true);
        access.visible(user, incidentId, true);
        Attachment attachment = findActive(incidentId, attachmentId);
        attachment.markAsDeleted();
        attachments.flush();
        record(user, attachment, "ATTACHMENT_DELETED");
    }

    /**
     * Resuelve la pareja de identificadores y oculta evidencias eliminadas.
     *
     * @param incidentId padre
     * @param id         evidencia
     * @return evidencia activa de ese padre
     */
    private Attachment findActive(UUID incidentId, UUID id) {
        return attachments.findByIdAndIncident_IdAndDeletedAtIsNull(id, incidentId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "Evidencia no encontrada."));
    }

    /**
     * Persiste el acontecimiento interno en la transacción de metadatos.
     *
     * @param actor      autor
     * @param attachment evidencia
     * @param action     evento interno, fuera del historial público
     */
    private void record(User actor, Attachment attachment, String action) {
        audit.saveAndFlush(new AuditEvent(actor, action, "ATTACHMENT", attachment.getId().toString(),
                Map.of("incidentId", attachment.getIncidentId().toString()),
                attachment.getDeletedAt() == null ? attachment.getCreatedAt() : attachment.getDeletedAt()));
    }

    /**
     * Construye un error de disponibilidad sin información del proveedor.
     *
     * @param code código estable
     * @return error público sin causas S3 ni claves
     */
    private static ApiException storageFailure(String code) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, "Almacenamiento temporalmente no disponible.");
    }

    /**
     * Descarta rutas, controles, separadores de línea y caracteres de formato;
     * limita por puntos Unicode.
     *
     * @param name nombre no confiable, nunca usado como clave ni ruta
     * @return nombre seguro de 1 a 128 caracteres, preservando extensión
     *         fotográfica si existe
     */
    static String sanitizeFilename(String name) {
        if (name == null)
            return "attachment";
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        StringBuilder safe = new StringBuilder();
        name.codePoints().filter(c -> !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT
                && Character.getType(c) != Character.SURROGATE && c != 0x2028 && c != 0x2029
                && c != '"' && c != ':' && c != ';').forEach(safe::appendCodePoint);
        String result = safe.toString().strip();
        if (result.isBlank() || result.equals(".") || result.equals(".."))
            return "attachment";
        if (result.codePointCount(0, result.length()) > 128) {
            int dot = result.lastIndexOf('.');
            String extension = dot >= 0 ? result.substring(dot) : "";
            if (!extension.matches("(?i)\\.(jpe?g|png)"))
                extension = "";
            result = result.substring(0, result.offsetByCodePoints(0, 128 - extension.length())) + extension;
        }
        return result;
    }

    /** Proyección interna; evita acceso lazy fuera de la transacción. */
    private record Download(String key, AttachmentDTO metadata) {
    }
}
