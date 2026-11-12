package es.grupo18.urbanpulse.attachment.model;

/**
 * Define los formatos de evidencia en UrbanPulse.
 */

public enum ContentType {

    /** Fotografía JPEG, incluidas las extensiones jpg y jpeg. */
    IMAGE_JPEG("image/jpeg"),

    /** Fotografía PNG, con transparencia cuando está presente. */
    IMAGE_PNG("image/png");

    // MIME público independiente del valor persistido.
    private final String mimeType;

    /**
     * Fija el enumerado a partir del tipo de contenido
     * expresado en formato MIME.
     *
     * @param mimeType Tipo de contenido en el formato MIME
     *                 Ejemplo: "image/jpeg".
     */
    ContentType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Obtiene el MIME público del formato persistido.
     *
     * @return string con el tipo en formato MIME
     */
    public String getMimeType() {
        return mimeType;
    }
}
