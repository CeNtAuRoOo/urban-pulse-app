package es.grupo18.urbanpulse.incidents.api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import es.grupo18.urbanpulse.shared.error.ProblemDetailFactory;
import es.grupo18.urbanpulse.shared.security.CachedBodyRequest;

/**
 * Acota la memoria antes de deserializar altas y correcciones. No consume cuotas
 * ni depende de Content-Length: también limita cuerpos transferidos por bloques.
 */
@RequiredArgsConstructor
public class IncidentBodyLimitFilter extends OncePerRequestFilter {
    /** Máximo bruto, incluido escape Unicode y espacios JSON: 64 KiB. */
    public static final int MAX_BODY_BYTES = 65_536;
    private final ProblemDetailFactory problems;

    /**
     * Selecciona las dos rutas de escritura del núcleo sin alcanzar Tramitación.
     *
     * @param request petición recibida, con posible context path
     * @return true para cualquier otra combinación de método y ruta
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("POST".equals(request.getMethod()) && path.equals("/api/v1/incidents"))
                && !("PATCH".equals(request.getMethod()) && path.matches("/api/v1/incidents/[^/]+"));
    }

    /**
     * Lee como máximo límite + 1 bytes y conserva solo las peticiones admitidas.
     *
     * @param request petición antes de MVC
     * @param response respuesta ProblemDetail 413 si se supera el límite
     * @param chain cadena de seguridad/controlador
     * @throws IOException si falla el transporte
     * @throws ServletException si falla la cadena posterior
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            problems.write(request, response, HttpStatus.valueOf(413), "PAYLOAD_TOO_LARGE",
                    "El cuerpo JSON no puede superar 65536 bytes.");
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }
}
