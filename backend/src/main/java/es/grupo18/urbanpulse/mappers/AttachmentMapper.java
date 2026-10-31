
import org.springframework.stereotype.Component;

import es.grupo18.urbanpulse.dto.attachment.AttachmentDTO;
import es.grupo18.urbanpulse.entity.model.Attachment;

@Component 
public class AttachmentMapper
{
    public AttachmentDTO toDTO(Attachment attachment) {

        return new AttachmentDTO(
                attachment.getId(),
                attachment.getIncident(),
                attachment.getOriginalFilename(),
                attachment.getContentType().getMimeType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt(),
                attachment.getDescription()
        );
    }
}