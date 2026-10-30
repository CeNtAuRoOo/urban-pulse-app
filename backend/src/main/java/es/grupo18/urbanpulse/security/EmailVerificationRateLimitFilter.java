package es.grupo18.urbanpulse.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import es.grupo18.urbanpulse.config.RateLimitProperties;
import es.grupo18.urbanpulse.exception.ProblemDetailFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cuotas de verificación después de validar JWT y antes del
 * controlador/HMAC/bloqueos. Se consume IP, luego usuario (en reenvío:
 * enfriamiento y presupuesto largo).Si un bucket posterior deniega, no se
 * restituyen los tokens anteriores: política conservadora. Reenviar nunca
 * modifica los buckets de confirmación. Los contadores son locales por
 * instancia y se pierden al reiniciar.
 */
@RequiredArgsConstructor
public class EmailVerificationRateLimitFilter extends OncePerRequestFilter {

    private final InMemoryRateLimiter limiter;
    private final RateLimitProperties limits;
    private final ProblemDetailFactory problems;

    /**
     * Determina si una petición debe excluirse del filtro de limitación
     * de confirmaciones y reenvíos.
     * Solo procesa los POST dirigidos a los dos endpoints de verificación.
     *
     * @param request petición HTTP recibida
     * @return true si la petición debe excluirse del filtro; false si debe
     *         procesarse
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || (!path.equals("/api/v1/auth/email-verifications/confirm")
                && !path.equals("/api/v1/auth/email-verifications/resend"));
    }

    /**
     * Aplica los límites de confirmación o reenvío al usuario autenticado
     * y a la dirección IP de conexión.
     * Rechaza las solicitudes que agotan alguno de los buckets antes
     * de continuar con el procesamiento de la petición.
     *
     * @param request  petición HTTP recibida
     * @param response respuesta HTTP
     * @param chain    cadena de filtros de la petición
     * @throws ServletException si se produce un error durante el filtrado
     * @throws IOException      si se produce un error de entrada o salida HTTP
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String user = jwt.getName();
            String ip = request.getRemoteAddr();
            boolean confirm = request.getRequestURI().endsWith("/confirm");
            boolean allowed = confirm
                    ? consume("confirm-ip:" + ip, limits.confirmIp(), request, response)
                            && consume("confirm-user:" + user, limits.confirmUser(), request, response)
                    : consume("resend-ip:" + ip, limits.resendIp(), request, response)
                            && consume("resend-cooldown:" + user, limits.resendCooldown(), request, response)
                            && consume("resend-user:" + user, limits.resendUser(), request, response);

            if (!allowed) {
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Intenta consumir un token del bucket identificado por la clave indicada.
     * Si el límite se ha agotado, devuelve HTTP 429 e informa del tiempo
     * de espera mediante la cabecera Retry-After.
     *
     * @param key      identificador del bucket que se desea consumir
     * @param policy   política de capacidad y reposición aplicable
     * @param request  petición HTTP recibida
     * @param response respuesta HTTP en la que se escribirá el error
     * @return true si se permite continuar; false si se rechaza la petición
     * @throws IOException si se produce un error al escribir la respuesta
     */
    private boolean consume(String key, RateLimitProperties.Policy policy, HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        var decision = limiter.acquire(key, policy);

        if (decision.allowed()) {
            return true;
        }

        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT", "Demasiadas solicitudes.");
        return false;
    }
}
