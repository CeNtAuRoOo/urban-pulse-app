package es.grupo18.urbanpulse.processing.dto;

import java.util.List;

import es.grupo18.urbanpulse.shared.pagination.PageMetadata;

/**
 * Página SQL de recursos autorizados.
 *
 * @param data       recursos de la página
 * @param pagination totales filtrados por incidencia
 */
public record PriorityDecisionPageDTO(List<PriorityDecisionDTO> data, PageMetadata pagination) {
}
