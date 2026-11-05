
package es.grupo18.urbanpulse.identity.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUpdateUserRequestTest {

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
    void rejectsNullActive() {
        var request = new AdminUpdateUserRequest(null);

        assertThat(validator.validate(request))
                .extracting(error -> error.getMessage())
                .contains("Debes indicar si la cuenta estará activa.");
    }

    @Test
    void acceptsActiveTrue() {
        var request = new AdminUpdateUserRequest(true);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsActiveFalse() {
        var request = new AdminUpdateUserRequest(false);

        assertThat(validator.validate(request)).isEmpty();
    }
}
