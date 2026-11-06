
package es.grupo18.urbanpulse.attachment.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import es.grupo18.urbanpulse.attachment.dto.AttachmentDTO;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.service.AttachmentService;
import es.grupo18.urbanpulse.service.attachment.AttachmentContent;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * Uploads a photograph.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDTO> attachPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false)
            @Size(max = 300) String description,
            @AuthenticationPrincipal User uploader) {

        AttachmentDTO response = (AttachmentDTO) attachmentService.attachPhoto(
                file,
                description,
                uploader
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{attachmentId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(response);
    }

    /**
     * Lists all active attachments.
     */
    @GetMapping
    public ResponseEntity<List<Object>> getAttachments() {

        return ResponseEntity.ok(
                attachmentService.getAttachments()
        );
    }

    /**
     * Retrieves attachment metadata.
     */
    @GetMapping("/{attachmentId}")
    public ResponseEntity<Object> getAttachment(
            @PathVariable UUID attachmentId) {

        return ResponseEntity.ok(
                attachmentService.getAttachment(attachmentId)
        );
    }

    /**
     * Retrieves the actual photograph from Neon.
     */
    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> getAttachmentContent(
            @PathVariable UUID attachmentId) {

        AttachmentContent content =
                attachmentService.getAttachmentContent(attachmentId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        content.contentType()
                ))
                .contentLength(content.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(content.fileName())
                                .build()
                                .toString()
                )
                .body(content.data());
    }
}
