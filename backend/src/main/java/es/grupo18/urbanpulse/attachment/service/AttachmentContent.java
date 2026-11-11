package es.grupo18.urbanpulse.attachment.service;

/**
 * Binario privado acotado a 10 MiB.
 *
 * @param fileName    nombre seguro
 * @param contentType MIME validado
 * @param sizeBytes   longitud real
 * @param data        imagen saneada
 */
public record AttachmentContent(
        String fileName,
        String contentType,
        long sizeBytes,
        byte[] data) {
}
