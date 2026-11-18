package es.grupo18.urbanpulse.urban_context.model;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKTReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regresiones de las invariantes de geometría y del aislamiento de la entidad.
 */
class UrbanAssetTest {
    private final GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 25830);

    private UrbanAsset crear(Geometry geometry) {
        return UrbanAsset.create("sintetico", null, null, null, null, null, null, null, geometry);
    }

    @Test
    void rechazaGeometriaInvalidaNoFinitaTridimensionalOSridDistinto() throws Exception {
        var reader = new WKTReader(factory);
        for (String text : new String[] { "POLYGON ((0 0,2 2,2 0,0 2,0 0))", "POINT (NaN 0)", "POINT (Infinity 0)",
                "POINT Z (0 0 1)", "POINT Z (0 0 Infinity)", "POINT M (0 0 1)" }) {
            assertThatThrownBy(() -> crear(reader.read(text))).isInstanceOf(IllegalArgumentException.class);
        }
        var wrongSrid = factory.createPoint(new Coordinate(0, 0));
        wrongSrid.setSRID(4326);
        assertThatThrownBy(() -> crear(wrongSrid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void admiteGeometriasBidimensionalesValidasYNoVacias() throws Exception {
        var reader = new WKTReader(factory);
        for (String text : new String[] { "POINT (0 0)", "LINESTRING (0 0,1 1)", "POLYGON ((0 0,1 0,1 1,0 0))",
                "MULTIPOINT ((0 0),(1 1))", "GEOMETRYCOLLECTION (POINT (0 0),LINESTRING (0 0,1 1))" }) {
            assertThat(crear(reader.read(text)).getGeom().isValid()).isTrue();
        }
    }

    @Test
    void conservaCopiaDefensivaAlCrearYAlConsultar() {
        var point = factory.createPoint(new Coordinate(10, 20));
        var asset = crear(point);
        point.getCoordinateSequence().setOrdinate(0, 0, 100);
        asset.getGeom().getCoordinate().x = 200;
        assertThat(asset.getGeom().getCoordinate().x).isEqualTo(10);
        assertThat(asset.getGeom().getSRID()).isEqualTo(25830);
    }
}
