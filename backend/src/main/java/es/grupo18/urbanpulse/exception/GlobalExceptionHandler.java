package es.grupo18.urbanpulse.exception;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.ErrorResponse;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Gestiona globalmente las excepciones producidas durante las peticiones REST.
 * Transforma los errores de negocio, validación y acceso en respuestas
 * HTTP uniformes mediante objetos ProblemDetail.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ProblemDetailFactory problems;

    /**
     * Gestiona las excepciones propias de la API.
     *
     * @param ex      excepción de la API
     * @param request petición HTTP que ha provocado el error
     * @return respuesta con el estado y los detalles de la excepción
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> api(ApiException ex, HttpServletRequest request) {
        return response(ex.status(), ex.code(), ex.getMessage(), request);
    }

    /**
     * Gestiona los errores de validación de los datos recibidos.
     * Devuelve un estado HTTP 422 con los campos que incumplen las restricciones.
     *
     * @param ex      excepción producida por la validación
     * @param request petición HTTP que ha provocado el error
     * @return respuesta con los errores de validación
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var problem = problems.create(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR", "Los datos no son válidos.",
                request.getRequestURI());

        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ProblemDetailFactory.FieldError(error.getField(),
                        error.getCode() == null ? "INVALID" : error.getCode(), "Valor no válido."))
                .toList());

        return ResponseEntity.unprocessableContent().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /**
     * Gestiona los errores de lectura y deserialización del cuerpo HTTP.
     * Devuelve 422 para propiedades JSON desconocidas y 400 para
     * el resto de errores de interpretación.
     *
     * @param ex      excepción producida al interpretar el cuerpo de la petición
     * @param request petición HTTP que ha provocado el error
     * @return respuesta con el error de entrada correspondiente
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException) {
                var problem = problems.create(HttpStatus.UNPROCESSABLE_CONTENT, "UNKNOWN_PROPERTY",
                        "La petición contiene campos no admitidos.", request.getRequestURI());

                problem.setProperty("errors",
                        List.of(new ProblemDetailFactory.FieldError("body", "UNKNOWN_PROPERTY",
                                "Campo no admitido.")));

                return ResponseEntity.unprocessableContent().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body(problem);
            }
        }

        return response(HttpStatus.BAD_REQUEST, "INVALID_JSON", "JSON no interpretable.", request);
    }

    /**
     * Gestiona los intentos de acceso a recursos sin permisos suficientes.
     *
     * @param ex      excepción de acceso denegado
     * @param request petición HTTP que ha provocado el error
     * @return respuesta HTTP 403 con los detalles del error
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> denied(AccessDeniedException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Permisos insuficientes.", request);
    }

    /**
     * Gestiona las excepciones no tratadas por otros manejadores.
     * Conserva el estado de los errores HTTP de cliente reconocidos
     * y devuelve 503 para indisponibilidad de PostgreSQL o 500 para otros errores.
     * Registra solo el tipo de excepción, sin mensajes, causas ni datos de
     * petición.
     *
     * @param ex      excepción producida durante la petición
     * @param request petición HTTP que ha provocado el error
     * @return respuesta HTTP con un mensaje de error genérico
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) {
            return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parámetro no válido.", request);
        }
        if (ex instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            return response(HttpStatus.valueOf(error.getStatusCode().value()), "HTTP_ERROR", "Petición no admitida.",
                    request);
        }

        if (AuthenticationBackendException.isDatabaseUnavailable(ex)) {
            log.warn("event=API_DATABASE_UNAVAILABLE exceptionType={}", ex.getClass().getName());
            return response(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Servicio temporalmente no disponible.", request);
        }

        log.error("event=API_INTERNAL_ERROR exceptionType={}", ex.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno.", request);
    }

    /**
     * Construye una respuesta HTTP de error utilizando ProblemDetail.
     * Añade la cabecera WWW-Authenticate cuando el estado es 401.
     *
     * @param status  estado HTTP de la respuesta
     * @param code    código identificador del error
     * @param detail  descripción del error
     * @param request petición HTTP que ha provocado el error
     * @return respuesta HTTP con los detalles del problema
     */
    private ResponseEntity<ProblemDetail> response(HttpStatus status, String code, String detail,
            HttpServletRequest request) {

        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);

        if (status == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        return response.body(problems.create(status, code, detail, request.getRequestURI()));
    }
}
