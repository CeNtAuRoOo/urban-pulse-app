package es.grupo18.urbanpulse.urban_context.mappers;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import es.grupo18.urbanpulse.urban_context.dto.UrbanAssetDTO;
import es.grupo18.urbanpulse.urban_context.model.UrbanAsset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrbanAssetMapperTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 25830);
    private final UrbanAssetMapper mapper = new UrbanAssetMapper();

    @Test
    void projectsAllScalarFields() {
        Point point = GF.createPoint(new Coordinate(439000.0, 4473000.0));
        UrbanAsset asset = UrbanAsset.create(
                "Ferrocarriles", "Estación", "cat", "desc",
                "Av. de Andalucía 1", "+34954000000", "info@example.com",
                "https://example.com", point);

        UrbanAssetDTO dto = mapper.toDTO(asset);

        assertThat(dto.sourceTable()).isEqualTo("Ferrocarriles");
        assertThat(dto.name()).isEqualTo("Estación");
        assertThat(dto.category()).isEqualTo("cat");
        assertThat(dto.description()).isEqualTo("desc");
        assertThat(dto.address()).isEqualTo("Av. de Andalucía 1");
        assertThat(dto.phone()).isEqualTo("+34954000000");
        assertThat(dto.email()).isEqualTo("info@example.com");
        assertThat(dto.url()).isEqualTo("https://example.com");
    }

    @Test
    void serializesPointAsSingleLineWktWithoutSridPrefix() {
        Point point = GF.createPoint(new Coordinate(439000.0, 4473000.0));
        UrbanAsset asset = UrbanAsset.create("Ferrocarriles", null, null, null, null, null, null, null, point);

        String wkt = mapper.toDTO(asset).geom();

        assertThat(wkt).startsWith("POINT (");
        assertThat(wkt).contains("439000");
        assertThat(wkt).contains("4473000");
        assertThat(wkt).doesNotContain("\n");
        assertThat(wkt).doesNotContain("SRID=");
    }

    @Test
    void geomIsAlwaysNonNullForPersistableAssets() {
        Point point = GF.createPoint(new Coordinate(439000.0, 4473000.0));
        UrbanAsset asset = UrbanAsset.create("Ferrocarriles", null, null, null, null, null, null, null, point);

        assertThat(mapper.toDTO(asset).geom()).isNotNull();
    }

    @Test
    void createRejectsNullGeometry() {
        assertThatThrownBy(() -> UrbanAsset.create(
                "Ferrocarriles", null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("geom is required");
    }

    @Test
    void createRejectsEmptyGeometry() {
        Point empty = GF.createPoint();
        assertThatThrownBy(() -> UrbanAsset.create(
                "Ferrocarriles", null, null, null, null, null, null, null, empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("geom is required");
    }

    @Test
    void createRejectsBlankSourceTable() {
        Point point = GF.createPoint(new Coordinate(439000.0, 4473000.0));
        assertThatThrownBy(() -> UrbanAsset.create(
                "  ", null, null, null, null, null, null, null, point))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceTable is required");
    }

    @Test
    void idIsNotYetAssignedForUnsavedEntity() {
        Point point = GF.createPoint(new Coordinate(439000.0, 4473000.0));
        UrbanAsset asset = UrbanAsset.create("Ferrocarriles", null, null, null, null, null, null, null, point);

        assertThat(mapper.toDTO(asset).id()).isNull();
    }
}
