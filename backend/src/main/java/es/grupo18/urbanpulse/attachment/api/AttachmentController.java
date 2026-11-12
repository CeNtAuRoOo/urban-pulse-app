package es.grupo18.urbanpulse.attachment.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.attachment.service.AttachmentService;

import static es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter.subject;
import static es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter.version;

/**
 * Cinco operaciones privadas anidadas; el servicio revalida la cuenta y
 * visibilidad vigente.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/incidents/{incidentId}/attachments")
@PreAuthorize("hasAuthority('EMAIL_VERIFIED')")
public class AttachmentController {
    private final AttachmentService service;

    /**
     * Adjunta una fotografía validada a la incidencia y devuelve su ubicación.
     *
     * @param jwt         principal JWT real
     * @param incidentId  padre visible
     * @param file        imagen JPEG/PNG
     * @param description descripción opcional
     * @return 201 con DTO y Location relativa al recurso anidado
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDTO> attachPhoto(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String description) {
        var result = service.attachPhoto(subject(jwt), version(jwt), incidentId, file, description);
        return ResponseEntity.created(URI.create("/api/v1/incidents/" + incidentId + "/attachments/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    /**
     * Lista las evidencias activas de una incidencia visible.
     *
     * @param jwt        principal
     * @param incidentId padre
     * @return metadatos activos sin paginación
     */
    @GetMapping
    public ResponseEntity<List<AttachmentDTO>> getAttachments(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getAttachments(subject(jwt), version(jwt), incidentId));
    }

    /**
     * Consulta una evidencia activa perteneciente exactamente a ese padre.
     *
     * @param jwt          principal
     * @param incidentId   padre
     * @param attachmentId evidencia
     * @return metadatos o 404
     */
    @GetMapping("/{attachmentId}")
    public ResponseEntity<AttachmentDTO> getAttachment(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @PathVariable UUID attachmentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getAttachment(subject(jwt), version(jwt), incidentId, attachmentId));
    }

    /**
     * Descarga el contenido autorizado con cabeceras privadas y nombre seguro.
     *
     * @param jwt          principal
     * @param incidentId   padre
     * @param attachmentId evidencia
     * @return binario privado o 404
     */
    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> getAttachmentContent(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @PathVariable UUID attachmentId) {
        var content = service.getAttachmentContent(subject(jwt), version(jwt), incidentId, attachmentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.contentType())).contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(content.data());
    }

    /**
     * Registra la baja lógica conservando el objeto y su trazabilidad.
     *
     * @param jwt          principal
     * @param incidentId   padre
     * @param attachmentId evidencia
     * @return 204; repetir devuelve 404
     */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID incidentId, @PathVariable UUID attachmentId) {
        service.deleteAttachment(subject(jwt), version(jwt), incidentId, attachmentId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
