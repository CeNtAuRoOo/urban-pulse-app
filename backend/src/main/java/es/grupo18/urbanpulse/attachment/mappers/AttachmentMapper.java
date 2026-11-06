package es.grupo18.urbanpulse.attachment.mappers;

import java.util.UUID;

import org.springframework.stereotype.Component;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.attachment.model.Attachment;

@Component
public class AttachmentMapper {
    public AttachmentDTO toDTO(Attachment attachment) {

        return new AttachmentDTO(
                attachment.getId(),
                UUID.randomUUID(), // TODO: Incident ID not yet added
                attachment.getOriginalFilename(),
                attachment.getContentType().getMimeType(),
                attachment.getSizeBytes(),
                UUID.randomUUID(), // TODO: User ID not yet added
                attachment.getCreatedAt(),
                attachment.getDescription());
    }
}