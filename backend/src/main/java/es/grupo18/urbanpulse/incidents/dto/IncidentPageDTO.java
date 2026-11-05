package es.grupo18.urbanpulse.incidents.dto;

import java.util.List;

import es.grupo18.urbanpulse.shared.pagination.PageMetadata;

/**
 * Página de resúmenes autorizados, con totales de la misma consulta SQL.
 *
 * @param data       página filtrada en SQL
 * @param pagination totales reales y página basada en 1
 */
public record IncidentPageDTO(List<IncidentSummaryDTO> data, PageMetadata pagination) {
}
