package es.grupo18.urbanpulse.shared.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Envoltorio de una petición HTTP que conserva su cuerpo en memoria,
 * permitiendo que los filtros y controladores posteriores puedan leerlo
 * después de haber sido inspeccionado por el limitador.
 */
public final class CachedBodyRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    /**
     * Crea un envoltorio de la petición HTTP que conserva una copia
     * del cuerpo previamente leído por el filtro.
     *
     * @param request petición HTTP original
     * @param body    contenido del cuerpo almacenado en memoria
     */
    public CachedBodyRequest(HttpServletRequest request, byte[] body) {
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
