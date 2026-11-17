
package es.grupo18.urbanpulse.urban_context.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

/**
 * Asset urbano unificado. Cada fila representa un elemento de alguna de las
 * tablas específicas del dataset (ferrocarriles, aparcamientos, etc.).
 *
 * Las tablas están unificadas en cartografia.contexto_urbano.
 *
 * La columna geom almacena geometrías en SRID 25830
 * (ETRS89 / UTM zone 30N).
 */
@Entity
@Table(name = "contexto_urbano", schema = "cartografia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UrbanAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "source_table", nullable = false, updatable = false, length = 64)
    private String sourceTable;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "url", length = 512)
    private String url;

    /**
     * Geometría espacial almacenada en PostGIS con SRID 25830.
     *
     * Se indica explícitamente el tipo JDBC para que Hibernate Spatial
     * gestione correctamente el enlace de geometrías JTS con PostgreSQL.
     */
    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "geom", columnDefinition = "geometry(Geometry,25830)")
    private Geometry geom;

    /**
     * Crea un asset urbano pendiente de persistir.
     *
     * @param sourceTable tabla de origen (no nula)
     * @param name        nombre opcional
     * @param category    categoría opcional
     * @param description descripción opcional
     * @param address     dirección textual opcional
     * @param phone       teléfono opcional
     * @param email       email opcional
     * @param url         URL opcional
     * @param geom        geometría en SRID 25830
     * @return nueva entidad sin identidad asignada
     */
    public static UrbanAsset create(
            String sourceTable,
            String name,
            String category,
            String description,
            String address,
            String phone,
            String email,
            String url,
            Geometry geom) {

        if (sourceTable == null || sourceTable.isBlank()) {
            throw new IllegalArgumentException("sourceTable is required");
        }

        if (geom == null || geom.isEmpty()) {
            throw new IllegalArgumentException("geom is required in UrbanAsset");
        }

        if (geom.getSRID() != 25830) {
            throw new IllegalArgumentException(
                    "geom must use SRID 25830; received: " + geom.getSRID());
        }

        UrbanAsset asset = new UrbanAsset();
        asset.sourceTable = sourceTable;
        asset.name = name;
        asset.category = category;
        asset.description = description;
        asset.address = address;
        asset.phone = phone;
        asset.email = email;
        asset.url = url;
        asset.geom = geom;

        return asset;
    }
}
