package es.grupo18.urbanpulse.attachment.mappers;

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
     * @return metadatos públicos, descripción nullable
     */
    public AttachmentDTO toDTO(Attachment attachment) {
        return new AttachmentDTO(
                attachment.getId(),
                attachment.getIncidentId(),
                attachment.getOriginalFilename(),
                attachment.getContentType().getMimeType(),
                attachment.getSizeBytes(),
                attachment.getUser().getId(),
                attachment.getCreatedAt(),
                attachment.getDescription());
    }
}
