package es.grupo18.urbanpulse.dto.user;

/**
 * DTO que representa los metadatos de paginación de una consulta.
 * Proporciona información sobre la página actual, el tamaño de página,
 * el número total de elementos y el número total de páginas disponibles.
 *
 * @param page       página actual, comenzando por 1
 * @param pageSize   cantidad máxima de elementos por página
 * @param totalItems número total de elementos que cumplen los filtros
 * @param totalPages número total de páginas disponibles
 */
public record PageMetadata(
        int page,
        int pageSize,
        long totalItems,
        int totalPages) {
}