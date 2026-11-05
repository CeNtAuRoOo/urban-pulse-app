package es.grupo18.urbanpulse.shared.pagination;

/**
 * Convención compartida de paginación por offset, con primera página 1.
 *
 * @param page       página solicitada
 * @param pageSize   tamaño solicitado
 * @param totalItems total tras aplicar filtros
 * @param totalPages cero si no hay resultados
 */
public record PageMetadata(
        int page,
        int pageSize,
        long totalItems,
        int totalPages) {
}
