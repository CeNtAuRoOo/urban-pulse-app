package es.grupo18.urbanpulse.invitations.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import es.grupo18.urbanpulse.shared.error.ProblemDetailFactory;
import es.grupo18.urbanpulse.shared.ratelimit.InMemoryRateLimiter;
import es.grupo18.urbanpulse.shared.ratelimit.RateLimitProperties;

/**
 * Cuotas de canje tras autenticar y antes de calcular HMAC o bloquear filas.
 */
@RequiredArgsConstructor
public class InvitationRateLimitFilter extends OncePerRequestFilter {

    private final InMemoryRateLimiter limiter;
    private final RateLimitProperties limits;
    private final ProblemDetailFactory problems;

    /**
     * Selecciona únicamente el POST de canje autenticado.
     *
     * @param request petición recibida
     * @return true para las demás rutas
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI()
                .substring(request.getContextPath().length()).equals("/api/v1/users/me/staff-invitation-redemptions");
    }

    /**
     * Consume IP y usuario en ese orden, sin reembolsar si el segundo rechaza.
     *
     * @param request  petición autenticada
     * @param response respuesta
     * @param chain    siguiente filtro
     * @throws IOException      si falla la escritura
     * @throws ServletException si falla la cadena
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwt
                && jwt.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("EMAIL_VERIFIED"))) {
            if (!consume("invitation-ip:" + request.getRemoteAddr(), limits.invitationIp(), request, response)
                    || !consume("invitation-user:" + jwt.getName(), limits.invitationUser(), request, response)) {
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Intenta consumir una cuota de peticiones para la clave indicada.
     * Si no quedan tokens, responde con HTTP 429 y la cabecera Retry-After.
     *
     * @param key      identificador del bucket de limitación
     * @param policy   política de capacidad y reposición aplicable
     * @param request  petición HTTP recibida
     * @param response respuesta HTTP en la que se comunica el rechazo
     * @return {@code true} si la petición puede continuar; {@code false} si
     *         se ha rechazado por exceso de peticiones
     * @throws IOException si falla la escritura de la respuesta HTTP
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
