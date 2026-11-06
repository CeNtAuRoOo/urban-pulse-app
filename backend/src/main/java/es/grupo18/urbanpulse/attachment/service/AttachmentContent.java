package es.grupo18.urbanpulse.attachment.service;

public record AttachmentContent(
                String fileName,
                String contentType,
                long sizeBytes,
                byte[] data) {
}