package es.grupo18.urbanpulse.maps.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import es.grupo18.urbanpulse.shared.error.ApiException;
import static org.assertj.core.api.Assertions.*;

class MapQueryTest {
    @ParameterizedTest
    @ValueSource(doubles = { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -91, 91 })
    void rejectsInvalidLatitude(double value) {
        assertThatThrownBy(() -> new MapQuery(value, -5, 38, -3, 8, null, null)).isInstanceOf(ApiException.class);
    }

    @Test
    void requiresOrderedFiniteCoordinatesAndValidZoom() {
        for (double[] b : new double[][] { {37,-5,36,-3}, {36,-3,37,-5}, {36,-5,36,-3},
                {36,-5,37,-5}, {36,-181,37,-3}, {36,-5,37,181}, {36,-5,91,-3},
                {36,Double.NaN,37,-3}, {36,-5,Double.NaN,-3}, {36,-5,37,Double.NaN} }) {
            assertThatThrownBy(() -> new MapQuery(b[0], b[1], b[2], b[3], 8, null, null))
                    .isInstanceOf(ApiException.class);
        }
        for (int zoom : new int[] {-1, 23}) {
            assertThatThrownBy(() -> new MapQuery(36, -6, 38, -3, zoom, null, null))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void wholeProvinceAtLowZoomAndNestedGrids() {
        var province = new MapQuery(36, -6, 38, -3, 8, null, null);
        assertThat(province.cellMeters()).isEqualTo(51200);
        assertThatThrownBy(() -> new MapQuery(36, -6, 38, -3, 18, null, null)).isInstanceOf(ApiException.class);
        var close = new MapQuery(36.72, -4.421, 36.721, -4.420, 18, null, null);
        assertThat(close.cellMeters()).isZero();
        for (int z = 8; z < 17; z++) {
            var q = new MapQuery(36.72, -4.421, 36.721, -4.420, z, null, null);
            var next = new MapQuery(36.72, -4.421, 36.721, -4.420, z + 1, null, null);
            assertThat(q.cellMeters()).isEqualTo(2 * next.cellMeters());
        }
    }
}
