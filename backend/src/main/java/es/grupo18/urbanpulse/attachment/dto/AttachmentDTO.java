
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
 * @param id          identificador único de la evidencia
 * @param incidentId  identificador de la incidencia asociada
 * @param fileName    nombre original del archivo
 * @param contentType tipo MIME de la fotografía
 * @param sizeBytes   tamaño del archivo en bytes
 * @param uploadedBy  información pública del usuario que subió la fotografía
 * @param createdAt   fecha de creación de la evidencia
 * @param description descripción accesible opcional de la fotografía
 */
public record AttachmentDTO(
        UUID id,
        UUID incidentId,
        String fileName,
        String contentType,
        long sizeBytes,
        UUID uploadedBy, // TODO: To be replaced with an User DTO.
        Instant createdAt,
        String description) {
}
