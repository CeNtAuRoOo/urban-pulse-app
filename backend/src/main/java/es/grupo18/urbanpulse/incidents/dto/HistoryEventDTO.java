package es.grupo18.urbanpulse.incidents.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;

/**
 * Proyección pública, nunca tabla ni serialización directa de AuditEvent.
 *
 * @param id         identidad del evento de auditoría
 * @param eventType  CREATED, UPDATED, REVIEWED, INFORMATION_RESPONDED o
 *                   PRIORITY_CHANGED
 * @param occurredAt instante UTC
 * @param actor      identidad mínima del informante original en
 *                   CREATED/UPDATED/
 *                   INFORMATION_RESPONDED; ausente para otros actores y para
 *                   REVIEWED/PRIORITY_CHANGED, con independencia de sus roles
 *                   actuales
 * @param summary    texto público definido por el servidor
 * @param details    versión, decisión/motivo público o nivel; nunca detalles
 *                   internos
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryEventDTO(UUID id, String eventType, Instant occurredAt, UserSummaryDTO actor,
        String summary, Map<String, String> details) {
}
