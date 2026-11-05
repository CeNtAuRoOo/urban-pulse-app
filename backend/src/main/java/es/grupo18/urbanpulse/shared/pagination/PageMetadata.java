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
    /**
     * Valida la convención compartida y el offset máximo de JPA sin desbordamiento.
     *
     * @param page     primera página 1
     * @param pageSize tamaño entre 1 y 100
     * @throws org.springframework.web.server.ResponseStatusException si los límites
     *                                                                no son válidos
     *                                                                (400)
     */
    public static void validate(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || ((long) page - 1) * pageSize > Integer.MAX_VALUE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "La página debe ser positiva, pageSize de 1 a 100 y el offset como máximo 2147483647.");
        }
    }
}
