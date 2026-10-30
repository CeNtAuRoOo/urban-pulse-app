package es.grupo18.urbanpulse.exception;

import org.springframework.http.HttpStatus;

/**
 * Representa un error de la API asociado a un estado HTTP y un código
 * identificador, permitiendo devolver respuestas de error uniformes.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * Crea una excepción de la API con un estado HTTP, un código
     * identificador y una descripción del error.
     *
     * @param status estado HTTP asociado al error
     * @param code   código identificador del error de la API
     * @param detail descripción del error que se devolverá al cliente
     */
    public ApiException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    /**
     * Obtiene el estado HTTP asociado a la excepción.
     *
     * @return estado HTTP del error
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * Obtiene el código identificador del error.
     *
     * @return código del error de la API
     */
    public String code() {
        return code;
    }

    /**
     * Crea una excepción de autenticación no válida con estado HTTP 401.
     *
     * @return excepción de autenticación no autorizada
     */
    public static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autenticación no válida.");
    }
}
