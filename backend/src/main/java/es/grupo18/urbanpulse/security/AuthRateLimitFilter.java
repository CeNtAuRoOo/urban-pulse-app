package es.grupo18.urbanpulse.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import es.grupo18.urbanpulse.config.RateLimitProperties;
import es.grupo18.urbanpulse.entity.model.User;
import es.grupo18.urbanpulse.exception.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Filtro de seguridad que limita las peticiones de registro e inicio de sesión.
 * Aplica políticas independientes por dirección IP y correo electrónico,
 * evitando solicitudes excesivas antes de procesarlas en los controladores.
 * Limita además el cuerpo de los cinco POST de autenticación; el cambio de
 * contraseña no consume tokens de los buckets.
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
     * @return true si no es uno de los cinco POST de autenticación; false en caso
     *         contrario
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || (!path.equals("/api/v1/auth/login")
                && !path.equals("/api/v1/auth/register") && !path.equals("/api/v1/users/me/password-changes")
                && !path.equals("/api/v1/auth/email-verifications/confirm")
                && !path.equals("/api/v1/auth/email-verifications/resend"));
    }

    /**
     * Comprueba los límites de peticiones por IP y, en el login, por correo.
     * Aplica un límite de 16 KiB al cuerpo de los cinco POST de autenticación,
     * exigiendo además un cuerpo vacío para las solicitudes de reenvío.
     * El cambio de contraseña, la confirmación y el reenvío no consumen
     * los buckets de registro y login.
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

        if (request.getRequestURI().endsWith("/email-verifications/resend") && body.length != 0) {
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

        chain.doFilter(new CachedRequest(request, body), response);
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

    /**
     * Envoltorio de una petición HTTP que conserva su cuerpo en memoria,
     * permitiendo que los filtros y controladores posteriores puedan leerlo
     * después de haber sido inspeccionado por el limitador.
     */
    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        /**
         * Crea un envoltorio de la petición HTTP que conserva una copia
         * del cuerpo previamente leído por el filtro.
         *
         * @param request petición HTTP original
         * @param body    contenido del cuerpo almacenado en memoria
         */
        private CachedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        /**
         * Proporciona un flujo de lectura del cuerpo HTTP almacenado en memoria.
         *
         * @return flujo de entrada con el contenido de la petición
         */
        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);

            return new ServletInputStream() {

                /**
                 * Lee el siguiente byte del cuerpo HTTP almacenado en memoria.
                 *
                 * @return siguiente byte disponible, o -1 si se ha alcanzado el final
                 */
                @Override
                public int read() {
                    return input.read();
                }

                /**
                 * Comprueba si se ha consumido completamente el cuerpo de la petición.
                 *
                 * @return true si no quedan bytes pendientes de lectura
                 */
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                /**
                 * Indica que el flujo de entrada está disponible para lectura síncrona.
                 *
                 * @return true, ya que el contenido se encuentra almacenado en memoria
                 */
                @Override
                public boolean isReady() {
                    return true;
                }

                /**
                 * Rechaza la configuración de un listener de lectura asíncrona,
                 * ya que este flujo solo admite operaciones síncronas.
                 *
                 * @param listener listener de lectura que se intenta registrar
                 * @throws UnsupportedOperationException siempre, porque no se admite
                 *                                       lectura asíncrona
                 */
                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Synchronous request body");
                }
            };
        }

        /**
         * Proporciona un lector de texto UTF-8 para el cuerpo HTTP almacenado.
         *
         * @return lector del contenido de la petición
         */
        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
