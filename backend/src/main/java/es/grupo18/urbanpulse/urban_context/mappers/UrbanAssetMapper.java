package es.grupo18.urbanpulse.urban_context.mappers;

import org.springframework.stereotype.Component;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;

import es.grupo18.urbanpulse.urban_context.dto.UrbanAssetDTO;
import es.grupo18.urbanpulse.urban_context.model.UrbanAsset;

/**
 * Proyección pública de un asset urbano unificado.
 *
 * <p>
 * {@link WKTWriter} no es thread-safe y mantiene estado interno (formato,
 * precisión). Se instancia por llamada: es barato y evita corrupción bajo
 * concurrencia.
 * </p>
 *
 * <p>
 * {@code geom} es obligatorio por contrato: {@link UrbanAsset#create} rechaza
 * assets sin geometría. El mapper no contempla {@code null}.
 * </p>
 */
@Component
public class UrbanAssetMapper {

    /**
     * Proyecta los campos públicos de un asset urbano almacenado en la tabla
     * unificada.
     *
     * <p>
     * La geometría se serializa como WKT (SRID 25830). El SRID no se incluye
     * en la cadena; se asume conocido por el contrato del DTO.
     * </p>
     *
     * @param asset entidad ya cargada desde la tabla unificada; su geometría
     *              nunca es nula por invariante del factory
     * @return metadatos públicos del asset con {@code geom} no nulo
     */
    public UrbanAssetDTO toDTO(UrbanAsset asset) {
        return new UrbanAssetDTO(
                asset.getId(),
                asset.getSourceTable(),
                asset.getName(),
                asset.getCategory(),
                asset.getDescription(),
                asset.getAddress(),
                asset.getPhone(),
                asset.getEmail(),
                asset.getUrl(),
                toWkt(asset.getGeom()));
    }

    private static String toWkt(Geometry geom) {
        // Invariante: UrbanAsset.create rechaza geom == null.
        WKTWriter writer = new WKTWriter();
        writer.setFormatted(false);
        return writer.write(geom);
    }
}
