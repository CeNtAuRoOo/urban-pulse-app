package es.grupo18.urbanpulse.security;

import java.io.IOException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import es.grupo18.urbanpulse.exception.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Gestiona los errores de autorización de Spring Security.
 * Devuelve una respuesta HTTP 403 en formato ProblemDetail cuando
 * un usuario autenticado no dispone de permisos suficientes.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailFactory problems;

    /**
     * Procesa un acceso denegado y escribe una respuesta HTTP 403.
     *
     * @param request   petición HTTP que ha provocado el error
     * @param response  respuesta HTTP en la que se escribirá el error
     * @param exception excepción de acceso denegado
     * @throws IOException si se produce un error al escribir la respuesta
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {

        problems.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Permisos insuficientes.");
    }
}
