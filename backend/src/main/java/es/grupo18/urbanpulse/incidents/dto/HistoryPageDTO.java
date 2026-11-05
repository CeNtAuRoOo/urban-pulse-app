package es.grupo18.urbanpulse.incidents.dto;

import java.util.List;

import es.grupo18.urbanpulse.shared.pagination.PageMetadata;

/**
 * Página de historial autorizado; nunca expone el JSONB interno de auditoría.
 *
 * @param data       eventos públicos ordenados por occurredAt ASC, id ASC
 * @param pagination totales filtrados
 */
public record HistoryPageDTO(List<HistoryEventDTO> data, PageMetadata pagination) {
}
