package es.grupo18.urbanpulse.urban_context.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.grupo18.urbanpulse.urban_context.model.UrbanAsset;

/**
 * Consultas de solo lectura sobre el catálogo de activos urbanos.
 *
 * <p>
 * {@code geom} es obligatorio por invariante de {@link UrbanAsset#create}, así que las queries no necesitan filtrar
 * {@code geom is not null}.
 * </p>
 */
public interface UrbanAssetRepository extends JpaRepository<UrbanAsset, Long> {

    /** Límite común de filas por consulta. */
    int MAX_RESULTS = 500;

    /**
     * Listado filtrado por tipo (tabla de origen) con tope duro.
     *
     * @param sourceTable tabla de origen; {@code null} para no filtrar
     * @param page        límite aplicado en SQL; el servicio solicita la primera página
     * @return activos ordenados por identidad, acotados antes de materializarlos
     */
    @Query("""
            select a from UrbanAsset a
            where (:sourceTable is null or a.sourceTable = :sourceTable)
            order by a.id
            """)
    List<UrbanAsset> searchBySourceTable(@Param("sourceTable") String sourceTable, Pageable page);

    /**
     * Búsqueda por proximidad. El punto se recibe en WGS84 (EPSG:4326) y se transforma a EPSG:25830 para comparar con
     * {@code geom}. Devuelve como máximo {@link #MAX_RESULTS} activos.
     *
     * <p>
     * La tabla debe coincidir con {@code @Table} de {@link UrbanAsset}.
     * </p>
     *
     * @param sourceTable tabla de origen; {@code null} para no filtrar
     * @param lon         longitud central en grados (WGS84)
     * @param lat         latitud central en grados (WGS84)
     * @param radius      radio en metros (1..5000)
     * @param maxResults  límite de filas solicitado por el servicio
     * @return activos dentro del radio, ordenados por distancia e identidad
     */
    @Query(value = """
            select a.*
            from cartografia.contexto_urbano a
            where (:sourceTable is null or a.source_table = :sourceTable)
              and ST_DWithin(
                    a.geom,
                    ST_Transform(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), 25830),
                    :radius)
            order by ST_Distance(
                    a.geom,
                    ST_Transform(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), 25830)), a.id
            limit :maxResults
            """, nativeQuery = true)
    List<UrbanAsset> searchByProximity(@Param("sourceTable") String sourceTable, @Param("lon") double lon,
            @Param("lat") double lat, @Param("radius") double radius, @Param("maxResults") int maxResults);
}
