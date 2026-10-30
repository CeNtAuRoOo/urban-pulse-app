package es.grupo18.urbanpulse.entity.enums;

/**
 * Define los formatos de evidencia en UrbanPulse. 
 */

public enum ContentType {
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png"),
    IMAGE_WEBP("image/webp");

    //Para poder pasar el enumerado al tipo MIME. :)
    private final String mimeType;

    /**
     * Fija el enumerado a partir del tipo de contenido
     * expresado en formato MIME.
     * @param mimeType Tipo de contenido en el formato MIME
     *      Ejemplo: "image/jpeg".
     */
    ContentType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * @return string con el tipo en formato MIME
     */
    public String getMimeType() {
        return mimeType;
    }
}