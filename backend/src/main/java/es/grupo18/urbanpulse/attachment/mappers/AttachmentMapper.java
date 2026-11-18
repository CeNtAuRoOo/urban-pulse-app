package es.grupo18.urbanpulse.attachment.mappers;

import java.util.UUID;

import org.springframework.stereotype.Component;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.attachment.model.Attachment;

/** Proyección pública sin claves de almacenamiento ni entidades JPA. */
@Component
public class AttachmentMapper {

    /**
     * Proyecta exclusivamente los campos públicos de una evidencia autorizada.
     *
     * @param attachment entidad con relaciones accesibles
     * @param reporterId informante original del padre ya autorizado, cargado una sola vez
     * @return metadatos públicos; uploadedBy es null si el autor no es el informante, independientemente de sus roles o
     *         actividad actuales
     */
    public AttachmentDTO toDTO(Attachment attachment, UUID reporterId) {
        return new AttachmentDTO(attachment.getId(), attachment.getIncidentId(), attachment.getOriginalFilename(),
                attachment.getContentType().getMimeType(), attachment.getSizeBytes(),
                reporterId.equals(attachment.getUser().getId()) ? reporterId : null, attachment.getCreatedAt(),
                attachment.getDescription());
    }
}
