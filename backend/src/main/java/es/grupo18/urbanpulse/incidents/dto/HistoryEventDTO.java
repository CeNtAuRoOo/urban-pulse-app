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
 * @param eventType  CREATED o UPDATED en esta entrega
 * @param occurredAt instante UTC
 * @param actor      identidad mínima, ausente para sistema
 * @param summary    texto público definido por el servidor
 * @param details    solo versión cuando consta y tiene formato permitido
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryEventDTO(UUID id, String eventType, Instant occurredAt, UserSummaryDTO actor,
        String summary, Map<String, String> details) {
}
