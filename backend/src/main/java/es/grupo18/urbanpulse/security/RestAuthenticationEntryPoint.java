package es.grupo18.urbanpulse.security;

import java.io.IOException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import es.grupo18.urbanpulse.exception.AuthenticationBackendException;
import es.grupo18.urbanpulse.exception.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Gestiona los errores de autenticación de Spring Security.
 * Devuelve 401 para credenciales no válidas, 503 para indisponibilidad del
 * backend
 * y 500 para fallos internos, siempre en formato ProblemDetail.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailFactory problems;

    /**
     * Responde al fallo de autenticación. Solo los errores 401 incluyen
     * WWW-Authenticate; los errores del backend se registran sin mensajes ni
     * causas.
     *
     * @param request   petición HTTP que ha provocado el error
     * @param response  respuesta HTTP en la que se escribirá el error
     * @param exception excepción de autenticación
     * @throws IOException si se produce un error al escribir la respuesta
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {

        if (exception instanceof AuthenticationServiceException) {
            boolean unavailable = exception instanceof AuthenticationBackendException backend
                    && backend.isUnavailable();

            String failureType = exception instanceof AuthenticationBackendException backend
                    ? backend.failureType()
                    : exception.getClass().getName();

            if (unavailable) {
                log.warn("event=AUTH_BACKEND_UNAVAILABLE exceptionType={}", failureType);
                problems.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                        "Servicio temporalmente no disponible.");
            }

            else {
                log.error("event=AUTH_BACKEND_ERROR exceptionType={}", failureType);
                problems.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno.");
            }

            return;
        }

        response.setHeader("WWW-Authenticate", "Bearer");
        problems.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autenticación no válida.");
    }
}
