package es.grupo18.urbanpulse.mappers;


import java.util.UUID;

import org.springframework.stereotype.Component;

import es.grupo18.urbanpulse.dto.attachment.AttachmentDTO;
import es.grupo18.urbanpulse.entity.model.Attachment;

@Component 
public class AttachmentMapper
{
    public AttachmentDTO toDTO(Attachment attachment) {

        return new AttachmentDTO(
                attachment.getId(),
                UUID.randomUUID(), //TODO: Incident ID not yet added
                attachment.getOriginalFilename(),
                attachment.getContentType().getMimeType(),
                attachment.getSizeBytes(),
                UUID.randomUUID(), //TODO: User ID not yet added 
                attachment.getCreatedAt(),
                attachment.getDescription()
        );
    }
}