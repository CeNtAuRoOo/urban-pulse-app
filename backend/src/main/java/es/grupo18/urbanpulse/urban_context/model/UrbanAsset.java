package es.grupo18.urbanpulse.urban_context.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

/**
 * Registro del catálogo espacial. La columna de origen identifica el conjunto
 * del que procede el activo.
 *
 * La columna geom almacena geometrías en SRID 25830
 * (ETRS89 / UTM zone 30N).
 */
@Entity
@Table(name = "contexto_urbano", schema = "cartografia")
@Getter
public class UrbanAsset {

    /** Constructor de persistencia utilizado por Hibernate al materializar una fila. */
    protected UrbanAsset() {
    }

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
    @Column(name = "geom", nullable = false, columnDefinition = "geometry(Geometry,25830)")
    private Geometry geom;

    /**
     * Evita que un consumidor modifique la geometría persistida fuera de la entidad.
     *
     * @return copia de la geometría válida en EPSG:25830
     */
    public Geometry getGeom() {
        return geom.copy();
    }

    /**
     * Crea un activo urbano pendiente de persistir.
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
     * @return nueva entidad sin identidad asignada y con copia defensiva de geometría
     * @throws IllegalArgumentException si el origen o la geometría incumplen las invariantes
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

        if (sourceTable == null || sourceTable.isBlank() || sourceTable.codePointCount(0, sourceTable.length()) > 64
                || !sourceTable.equals(sourceTable.strip()) || sourceTable.codePoints().anyMatch(code ->
                    Character.isISOControl(code) || (code >= 0xD800 && code <= 0xDFFF))) {
            throw new IllegalArgumentException("sourceTable debe ser un origen no vacío de hasta 64 puntos Unicode válidos.");
        }

        if (geom == null || geom.isEmpty()) {
            throw new IllegalArgumentException("geom es obligatoria y no puede estar vacía.");
        }

        if (geom.getSRID() != 25830) {
            throw new IllegalArgumentException(
                    "geom debe usar SRID 25830; recibido: " + geom.getSRID());
        }

        if (!geom.isValid() || java.util.Arrays.stream(geom.getCoordinates()).anyMatch(coordinate ->
                !Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y) || !Double.isNaN(coordinate.getZ())
                || !Double.isNaN(coordinate.getM()))) {
            throw new IllegalArgumentException("geom debe ser válida, bidimensional y tener coordenadas finitas.");
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
        asset.geom = geom.copy();

        return asset;
    }
}
