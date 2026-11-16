package es.grupo18.urbanpulse.urban_context.dto;

/**
 * Representa los datos de cualquier asset urbano, todos los assets urbanos que
 * están contenidos en la
 * base de datos en diferentes tablas están unificados en una tabla en el
 * backend que es la que utilizamos.
 * No expone la URL del backend obviamente.
 * privado.
 *
 * @param id          identificador único global para assets urbanos
 * @param sourceTable nombre de la tabla en la que se almacena el asset
 *                    urbano específico. (Por ejemplo si es un ferrocarril
 *                    source_table: "Ferrocarriles")
 * @param name        Parámetro opcional que algunos assets del dataset tienen y
 *                    otros no. Por ejemplo en el caso de
 *                    aparcamientos_publicos_municipales algún elemento tiene de
 *                    nombre "Camas" o "Av. de Andalucía"
 * @param category    Ahora mismo categoría realmente no debería ser usado, los
 *                    valores están sucios. Simplemente sirve por si después se
 *                    quiere añadir algún tipo de identificador, pero con los
 *                    nombres de tabla debería de ser suficiente.
 * @param description Descripción opcional. Por ejemplo podría ser la dirección
 *                    en texto de una calle o avenida como en algunos casos.
 * @param address     Puede guardar un correo por ejemplo. No es usado en muchos
 *                    casos.
 * @param url         Puede guardar una url. No es usado en muchos casos.
 * @param geom        Campo obligatorio con la geolocalización en SRID 25830,
 *                    serializada como WKT. Nunca es {@code null}: el factory
 *                    de {@code UrbanAsset} rechaza assets sin geometría.
 */
public record UrbanAssetDTO(
        Long id,
        String sourceTable,
        String name,
        String category,
        String description,
        String address,
        String phone,
        String email,
        String url,
        String geom) {
}
