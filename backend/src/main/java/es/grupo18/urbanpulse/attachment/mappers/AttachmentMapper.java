package es.grupo18.urbanpulse.attachment.mappers;

import org.springframework.stereotype.Component;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.attachment.model.Attachment;

@Component
public class AttachmentMapper {

    public AttachmentDTO toDTO(Attachment attachment) {
        return new AttachmentDTO(
                attachment.getId(),
                attachment.getIncidentId(),
                attachment.getOriginalFilename(),
                attachment.getContentType().getMimeType(),
                attachment.getSizeBytes(),
                attachment.getUser() == null ? null : attachment.getUser().getId(),
                attachment.getCreatedAt(),
                attachment.getDescription());
    }
}
