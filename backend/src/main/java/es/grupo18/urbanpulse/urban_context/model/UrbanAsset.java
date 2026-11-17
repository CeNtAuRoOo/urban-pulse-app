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

import org.locationtech.jts.geom.Geometry;

/**
 * Asset urbano unificado. Cada fila representa un elemento de alguna de las
 * tablas específicas del dataset (ferrocarriles, aparcamientos, etc.)
 * Estas tablas están unificadas: en {@code contexto_urbano}.
 *
 * <p>
 * La columna {@code geom} almacena geometría en SRID 25830 (ETRS89 / UTM
 * zone 30N). El DTO la proyecta como WKT.
 * </p>
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

    // Tabla de origen del asset (p. ej. "Ferrocarriles")
    @Column(name = "source_table", nullable = false, updatable = false, length = 64)
    private String sourceTable;

    // Nombre opcional; no todos los assets lo tienen
    @Column(name = "name", length = 255)
    private String name;

    // Categoría; actualmente con valores sucios, no usar para lógica
    @Column(name = "category", length = 64)
    private String category;

    // Descripción opcional (a veces dirección en texto)
    @Column(name = "description", length = 500)
    private String description;

    // Dirección textual opcional
    @Column(name = "address", length = 255)
    private String address;

    // Teléfono opcional
    @Column(name = "phone", length = 32)
    private String phone;

    // Email opcional
    @Column(name = "email", length = 128)
    private String email;

    // URL opcional
    @Column(name = "url", length = 512)
    private String url;

    // Geometría en SRID 25830. Nullable a nivel de esquema; en la práctica no hay
    // nulos.
    @Column(name = "geom", columnDefinition = "geometry(Geometry,25830)")
    private Geometry geom;

    /**
     * Crea un asset urbano pendiente de persistir.
     *
     * @param sourceTable tabla de origen (no nula)
     * @param name        nombre opcional
     * @param category    categoría opcional
     * @param description descripción opcional
     * @param address     dirección opcional
     * @param phone       teléfono opcional
     * @param email       email opcional
     * @param url         URL opcional
     * @param geom        geometría en SRID 25830 (puede ser nula según esquema)
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

        // La tabla debe tener un sourceTable y una geometría para ser válidas para el
        // uso en la aplicación.
        if (sourceTable == null || sourceTable.isBlank()) {
            throw new IllegalArgumentException("sourceTable is required");
        }

        if (geom == null || geom.isEmpty()) {
            throw new IllegalArgumentException("geom is required in UrbanAsset");
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
