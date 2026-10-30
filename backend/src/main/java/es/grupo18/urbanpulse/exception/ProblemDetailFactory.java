package es.grupo18.urbanpulse.exception;

import java.io.IOException;
import java.net.URI;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Centraliza la creación y serialización de respuestas de error
 * en formato ProblemDetail, siguiendo el estándar de errores HTTP
 * utilizado por la API REST.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailFactory {

    private final JsonMapper mapper;

    /**
     * Construye un ProblemDetail con el estado HTTP, código y descripción
     * del error, incluyendo opcionalmente la ruta de la petición.
     *
     * @param status estado HTTP del error
     * @param code   código identificador del error
     * @param detail descripción del error
     * @param path   ruta de la petición, o null si no debe incluirse
     * @return objeto ProblemDetail con los datos del error
     */
    public ProblemDetail create(HttpStatus status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);

        if (path != null) {
            problem.setInstance(URI.create(path));
        }

        return problem;
    }

    /**
     * Escribe directamente una respuesta HTTP de error en formato ProblemDetail.
     * Establece el estado y el tipo de contenido antes de serializar el resultado.
     *
     * @param request  petición HTTP que ha provocado el error
     * @param response respuesta HTTP en la que se escribirá el error
     * @param status   estado HTTP del error
     * @param code     código identificador del error
     * @param detail   descripción del error
     * @throws IOException si se produce un error al escribir la respuesta
     */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String detail) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), create(status, code, detail, request.getRequestURI()));
    }

    /**
     * Representa un error de validación asociado a un campo de una petición.
     *
     * @param field   nombre del campo que contiene el error
     * @param code    código identificador del error de validación
     * @param message descripción del error
     */
    public record FieldError(String field, String code, String message) {
    }
}
