package es.grupo18.urbanpulse.organization.model;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    @Test
    void generatesUuidVersionSevenWithRfcVariant() {
        UUID id = org.hibernate.id.uuid.UuidVersion7Strategy.INSTANCE.generateUuid(null);

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }
}
