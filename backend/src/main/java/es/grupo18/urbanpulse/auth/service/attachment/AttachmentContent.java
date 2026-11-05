package es.grupo18.urbanpulse.service.attachment;

public record AttachmentContent(
        String fileName,
        String contentType,
        long sizeBytes,
        byte[] data
) {}