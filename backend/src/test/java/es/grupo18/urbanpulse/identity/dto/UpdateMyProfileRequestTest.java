
package es.grupo18.urbanpulse.identity.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateMyProfileRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void rejectsNullDisplayName() {
        var request = new UpdateMyProfileRequest(null);

        assertThat(validator.validate(request))
                .extracting(error -> error.getMessage())
                .contains("El nombre no puede estar vacío.");
    }

    @Test
    void rejectsBlankDisplayName() {
        var request = new UpdateMyProfileRequest("   ");

        assertThat(validator.validate(request))
                .extracting(error -> error.getMessage())
                .contains("El nombre no puede estar vacío.");
    }

    @Test
    void rejectsDisplayNameShorterThanTwoCharacters() {
        var request = new UpdateMyProfileRequest("A");

        assertThat(validator.validate(request))
                .extracting(error -> error.getMessage())
                .contains("El nombre debe tener entre 2 y 120 caracteres.");
    }

    @Test
    void rejectsDisplayNameLongerThan120Characters() {
        var request = new UpdateMyProfileRequest("A".repeat(121));

        assertThat(validator.validate(request))
                .extracting(error -> error.getMessage())
                .contains("El nombre debe tener entre 2 y 120 caracteres.");
    }

    @Test
    void acceptsValidDisplayName() {
        var request = new UpdateMyProfileRequest("Ana Pérez");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsDisplayNameWithMinimumLength() {
        var request = new UpdateMyProfileRequest("AB");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsDisplayNameWithMaximumLength() {
        var request = new UpdateMyProfileRequest("A".repeat(120));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsNameThatBecomesTooShortAfterNormalization() {
        assertThat(validator.validate(new UpdateMyProfileRequest(" A "))).isNotEmpty();
    }

    @Test
    void countsUnicodeCodePointsInsteadOfUtf16Units() {
        assertThat(validator.validate(new UpdateMyProfileRequest("😀".repeat(120)))).isEmpty();
        assertThat(validator.validate(new UpdateMyProfileRequest("😀".repeat(121)))).isNotEmpty();
    }
}
