package es.grupo18.urbanpulse.incidents.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentETagsTest {
    @Test
    void strongOpaqueRoundTripAndMissingHeader() {
        assertThat(IncidentETags.parse(null)).isNull();
        assertThat(IncidentETags.parse(List.of())).isNull();
        String tag = "\"sha256-" + "a".repeat(64) + "\"";
        assertThat(IncidentETags.parse(List.of(tag))).isEqualTo(tag);
        assertThat(IncidentETags.parse(List.of(" " + tag + "\t"))).isEqualTo(tag);
    }

    @Test
    void rejectsWildcardsWeakListsOverflowAndUnsupportedFormats() {
        for (String value : List.of("*", "W/\"1\"", "1", "\"01\"", "\"-1\"", "\"1\",\"2\"", "",
                "\"9223372036854775808\"", "\"timestamp\"", "\"sha256-abc\"", "\"sha256-" + "A".repeat(64) + "\"")) {
            assertThatThrownBy(() -> IncidentETags.parse(List.of(value))).isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(400));
        }
        assertThatThrownBy(() -> IncidentETags.parse(List.of("\"0\"", "\"0\""))).isInstanceOf(ApiException.class);
    }
}
