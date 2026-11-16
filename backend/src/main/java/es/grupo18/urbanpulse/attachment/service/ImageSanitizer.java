package es.grupo18.urbanpulse.attachment.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import es.grupo18.urbanpulse.attachment.model.ContentType;
import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Decodifica JPEG/PNG y recodifica solo píxeles, sin EXIF/GPS ni metadatos
 * originales.
 */
@Component
public class ImageSanitizer {

    /** Límite independiente para entrada y resultado almacenado: 10 MiB. */
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    /**
     * Dos rasters de hasta unos 64 MB cada uno; evita el antiguo techo de 40 MP.
     */
    public static final long MAX_IMAGE_PIXELS = 16_000_000L;
    /** Acota también imágenes extremadamente estrechas o altas. */
    public static final int MAX_DIMENSION = 8192;
    private static final byte[] PNG_SIGNATURE = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };

    /**
     * Identifica los únicos formatos admitidos a partir de sus firmas.
     *
     * @param bytes contenido original; nunca se usa el MIME o extensión del cliente
     * @return formato reconocido por firma binaria
     * @throws ApiException 413 por bytes excesivos; 422 por firma desconocida o
     *                      vacía
     */
    public ContentType detectContentType(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            throw invalid();
        if (bytes.length > MAX_BYTES)
            throw tooLarge();
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255)
            return ContentType.IMAGE_JPEG;
        if (bytes.length >= 8 && Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(bytes, 8)))
            return ContentType.IMAGE_PNG;
        throw invalid();
    }

    /**
     * Rechaza truncamientos y advertencias del decodificador; acota dimensiones
     * antes del raster.
     * Conserva transparencia PNG y recodifica sin copiar metadatos, incluso
     * perfiles originales.
     *
     * @param bytes imagen de entrada de hasta 10 MiB
     * @param type  formato detectado por firma
     * @return imagen válida, sin metadatos originales, de hasta 10 MiB
     * @throws ApiException 422 por imagen inválida/dimensiones; 413 por entrada o
     *                      salida excesiva
     */
    public byte[] sanitize(byte[] bytes, ContentType type) {
        if (detectContentType(bytes) != type)
            throw invalid();
        String format = type == ContentType.IMAGE_JPEG ? "jpeg" : "png";
        try {
            if (type == ContentType.IMAGE_PNG)
                validatePng(bytes);
            else if (bytes.length < 4 || (bytes[bytes.length - 2] & 255) != 255
                    || (bytes[bytes.length - 1] & 255) != 217)
                throw invalid();
            BufferedImage original;
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext())
                    throw invalid();
                var reader = readers.next();
                try {
                    reader.setInput(input, false, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                            || (long) width * height > MAX_IMAGE_PIXELS
                            || !format.equalsIgnoreCase(reader.getFormatName()))
                        throw invalid();
                    reader.addIIOReadWarningListener((source, warning) -> {
                        throw invalid();
                    });
                    original = reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
            if (original == null)
                throw invalid();
            BufferedImage clean = new BufferedImage(original.getWidth(), original.getHeight(),
                    type == ContentType.IMAGE_PNG ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = clean.createGraphics();
            try {
                graphics.drawImage(original, 0, 0, null);
            } finally {
                graphics.dispose();
                original.flush();
            }
            var writers = ImageIO.getImageWritersByFormatName(format);
            if (!writers.hasNext())
                throw new IllegalStateException("Missing standard image writer");
            var writer = writers.next();
            try (var output = new ByteArrayOutputStream(); var imageOutput = new BoundedImageOutput(output)) {
                writer.setOutput(imageOutput);
                writer.write(null, new IIOImage(clean, null, null), writer.getDefaultWriteParam());
                imageOutput.flush();
                return output.toByteArray();
            } finally {
                writer.dispose();
                clean.flush();
            }
        } catch (IOException | IllegalArgumentException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof ApiException api)
                    throw api;
            }
            throw invalid();
        }
    }

    /**
     * ImageIO puede ignorar CRC o IEND ausente: verifica todos los chunks sin
     * reservar por su longitud.
     *
     * @param bytes PNG acotado
     * @throws ApiException si hay CRC, longitud, cabecera o terminación inválidos
     */
    private static void validatePng(byte[] bytes) {
        int offset = 8;
        boolean first = true;
        while (offset <= bytes.length - 12) {
            long length = unsignedInt(bytes, offset);
            if (length > bytes.length - offset - 12)
                throw invalid();
            int size = (int) length;
            String chunk = new String(bytes, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (first && (!chunk.equals("IHDR") || size != 13))
                throw invalid();
            first = false;
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, size + 4);
            if (crc.getValue() != unsignedInt(bytes, offset + 8 + size))
                throw invalid();
            offset += size + 12;
            if (chunk.equals("IEND")) {
                if (size != 0 || offset != bytes.length)
                    throw invalid();
                return;
            }
        }
        throw invalid();
    }

    /**
     * Lee la longitud o CRC de un chunk sin reservar memoria adicional.
     *
     * @param bytes  entrada
     * @param offset posición válida
     * @return entero big-endian sin signo
     */
    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 255) << 24) | ((long) (bytes[offset + 1] & 255) << 16)
                | ((long) (bytes[offset + 2] & 255) << 8) | (bytes[offset + 3] & 255);
    }

    /**
     * Construye el rechazo uniforme de entrada o salida excesiva.
     *
     * @return error estable de tamaño, también usado por la lectura multipart
     *         acotada
     */
    public static ApiException tooLarge() {
        return new ApiException(HttpStatus.valueOf(413), "ATTACHMENT_TOO_LARGE",
                "La fotografía no puede superar 10 MiB.");
    }

    /**
     * Construye el rechazo uniforme de contenido no válido.
     *
     * @return rechazo de contenido sin detalles de bibliotecas ni datos recibidos
     */
    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ATTACHMENT_FORMAT",
                "La fotografía debe ser JPEG o PNG válida, de hasta 16 millones de píxeles y 8192 por lado.");
    }

    /**
     * Acota la caché del escritor antes de asignarla, incluyendo escrituras con
     * seek.
     */
    static final class BoundedImageOutput extends MemoryCacheImageOutputStream {
        /**
         * Crea una caché de escritura limitada sobre el destino.
         *
         * @param output destino en memoria
         */
        BoundedImageOutput(ByteArrayOutputStream output) {
            super(output);
        }

        /**
         * Rechaza una escritura antes de superar la capacidad permitida.
         *
         * @param length bytes a escribir
         * @throws IOException si no se puede consultar la posición
         */
        private void check(int length) throws IOException {
            if (getStreamPosition() + length > MAX_BYTES)
                throw tooLarge();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(int value) throws IOException {
            check(1);
            super.write(value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            check(length);
            super.write(bytes, offset, length);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void seek(long position) throws IOException {
            if (position > MAX_BYTES)
                throw tooLarge();
            super.seek(position);
        }
    }
}
