
package es.grupo18.urbanpulse.attachment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa los datos de una evidencia fotográfica autorizados
 * para una respuesta concreta de la API.
 *
 * No expone la clave interna de almacenamiento ni URLs del bucket
 * privado.
 *
 * @param id               identificador único de la evidencia
 * @param incidentId       identificador de la incidencia asociada
 * @param originalFilename nombre original del archivo
 * @param contentType      tipo MIME de la fotografía
 * @param sizeBytes        tamaño del archivo en bytes
 * @param uploadedBy       UUID del usuario que subió la fotografía
 * @param createdAt        fecha de creación de la evidencia
 * @param description      descripción opcional; se serializa null si está
 *                         ausente
 */
public record AttachmentDTO(
        UUID id,
        UUID incidentId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        UUID uploadedBy,
        Instant createdAt,
        String description) {
}
