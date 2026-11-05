package es.grupo18.urbanpulse.auth.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.shared.error.ProblemDetailFactory;
import es.grupo18.urbanpulse.shared.ratelimit.InMemoryRateLimiter;
import es.grupo18.urbanpulse.shared.ratelimit.RateLimitProperties;
import es.grupo18.urbanpulse.shared.security.CachedBodyRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Filtro de seguridad que limita las peticiones de registro e inicio de sesión.
 * Aplica políticas independientes por dirección IP y correo electrónico,
 * evitando solicitudes excesivas antes de procesarlas en los controladores.
 * Limita además el cuerpo de los cinco POST de autenticación y los tres de
 * invitaciones; solo registro/login consumen tokens de estos buckets.
 */
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_BYTES = 16_384;
    private final InMemoryRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ProblemDetailFactory problems;
    private final JsonMapper mapper;

    /**
     * Determina si una petición debe excluirse del filtro de limitación.
     *
     * @param request petición HTTP recibida
     * @return true si no es un POST de autenticación o invitaciones protegido
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || (!path.equals("/api/v1/auth/login")
                && !path.equals("/api/v1/auth/register") && !path.equals("/api/v1/users/me/password-changes")
                && !path.equals("/api/v1/auth/email-verifications/confirm")
                && !path.equals("/api/v1/auth/email-verifications/resend")
                && !path.equals("/api/v1/staff-invitations")
                && !path.equals("/api/v1/users/me/staff-invitation-redemptions")
                && !path.matches("/api/v1/staff-invitations/[^/]+/revocations"));
    }

    /**
     * Comprueba los límites de peticiones por IP y, en el login, por correo.
     * Aplica un límite de 16 KiB al cuerpo de los POST de autenticación e
     * invitaciones,
     * exigiendo cuerpo vacío para reenvío de verificación y revocación de
     * invitaciones.
     * Las operaciones distintas de registro/login no consumen sus buckets;
     * las cuotas de confirmación, reenvío y canje se aplican en filtros
     * posteriores.
     * Conserva el cuerpo de las peticiones admitidas para su lectura posterior.
     *
     * @param request  petición HTTP recibida
     * @param response respuesta HTTP
     * @param chain    cadena de filtros de la petición
     * @throws ServletException si se produce un error durante el filtrado
     * @throws IOException      si se produce un error de lectura o escritura HTTP
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        boolean login = request.getRequestURI().endsWith("/login");
        boolean register = request.getRequestURI().endsWith("/register");

        if (login || register) {
            var decision = limiter.acquire((login ? "login-ip:" : "register-ip:") + request.getRemoteAddr(),
                    login ? properties.loginIp() : properties.registerIp());

            if (!decision.allowed()) {
                reject(request, response, decision);
                return;
            }
        }

        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);

        if (body.length > MAX_BODY_BYTES) {
            problems.write(request, response, HttpStatus.BAD_REQUEST, "INVALID_JSON", "Cuerpo JSON demasiado grande.");
            return;
        }

        if ((request.getRequestURI().endsWith("/email-verifications/resend")
                || request.getRequestURI().endsWith("/revocations")) && body.length != 0) {
            problems.write(request, response, HttpStatus.BAD_REQUEST, "INVALID_JSON", "La petición no admite cuerpo.");
            return;
        }

        if (login) {
            try {
                var root = mapper.readTree(body);
                var email = root == null ? null : root.get("email");

                if (email != null && email.isString() && User.normalizeEmail(email.asString()).length() <= 254) {
                    var decision = limiter.acquire("login-email:" + User.normalizeEmail(email.asString()),
                            properties.loginEmail());

                    if (!decision.allowed()) {
                        reject(request, response, decision);
                        return;
                    }
                }
            }

            catch (JacksonException ex) {
                problems.write(request, response, HttpStatus.BAD_REQUEST, "INVALID_JSON", "JSON no interpretable.");
                return;
            }
        }

        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /**
     * Rechaza una petición que ha superado su límite, devolviendo HTTP 429
     * e indicando el tiempo de espera mediante la cabecera Retry-After.
     *
     * @param request  petición HTTP rechazada
     * @param response respuesta HTTP
     * @param decision resultado del limitador de peticiones
     * @throws IOException si se produce un error al escribir la respuesta
     */
    private void reject(HttpServletRequest request, HttpServletResponse response, InMemoryRateLimiter.Decision decision)
            throws IOException {

        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT", "Demasiadas solicitudes.");
    }

}
