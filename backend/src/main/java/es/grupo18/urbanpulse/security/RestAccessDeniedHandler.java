package es.grupo18.urbanpulse.security;

import java.io.IOException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import es.grupo18.urbanpulse.exception.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Gestiona los errores de autorización de Spring Security.
 * Devuelve respuestas HTTP 403 en formato ProblemDetail, diferenciando
 * las cuentas que todavía no han verificado su correo electrónico
 * de aquellas que carecen de los permisos necesarios.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailFactory problems;

    /**
     * Procesa un acceso denegado y escribe una respuesta HTTP 403.
     * Devuelve EMAIL_NOT_VERIFIED cuando la identidad JWT autenticada
     * carece de la autoridad de verificación y FORBIDDEN en los demás casos.
     *
     * @param request   petición HTTP que ha provocado el error
     * @param response  respuesta HTTP en la que se escribirá el error
     * @param exception excepción de acceso denegado
     * @throws IOException si se produce un error al escribir la respuesta
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean unverified = authentication instanceof JwtAuthenticationToken && authentication.getAuthorities()
                .stream().noneMatch(a -> a.getAuthority().equals(DatabaseJwtAuthenticationConverter.EMAIL_VERIFIED));

        problems.write(request, response, HttpStatus.FORBIDDEN,
                unverified ? "EMAIL_NOT_VERIFIED" : "FORBIDDEN",
                unverified ? "Debes verificar tu correo para acceder a este recurso." : "Permisos insuficientes.");
    }
}
