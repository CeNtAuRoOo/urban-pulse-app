package es.grupo18.urbanpulse.auth.service;

import java.security.SecureRandom;
import java.util.*;

import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.auth.config.EmailVerificationProperties;
import es.grupo18.urbanpulse.auth.dto.ConfirmEmailRequest;
import es.grupo18.urbanpulse.auth.mail.EmailVerificationRequested;
import es.grupo18.urbanpulse.shared.mail.EmailMessage;
import es.grupo18.urbanpulse.shared.security.JwtProperties;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationCodeServiceTest {
    private EmailVerificationProperties properties() throws Exception {
        return new EmailVerificationProperties(PropertiesLoaderUtils.loadAllProperties("auth-test.properties")
                .getProperty("security.email-verification.secret"), 100, 10);
    }

    private JwtProperties jwt() throws Exception {
        return new JwtProperties("tests", PropertiesLoaderUtils.loadAllProperties("auth-test.properties")
                .getProperty("security.jwt.secret"), 7200, List.of("http://localhost"));
    }

    @Test
    void generatesFullRangeWithLeadingZerosAndBindsHmacToUserAndCode() throws Exception {
        var codes = new EmailVerificationCodeService(properties(), jwt());
        var random = mock(SecureRandom.class);
        ReflectionTestUtils.setField(codes, "random", random);
        when(random.nextInt(100_000_000)).thenReturn(0, 99_999_999);
        assertThat(codes.generate()).isEqualTo("00000000");
        assertThat(codes.generate()).isEqualTo("99999999");
        verify(random, times(2)).nextInt(100_000_000);
        UUID user = UUID.randomUUID();
        byte[] hash = codes.hash(user, "01234567");
        assertThat(hash).hasSize(32);
        assertThat(codes.matches(user, "01234567", hash)).isTrue();
        assertThat(codes.matches(UUID.randomUUID(), "01234567", hash)).isFalse();
        assertThat(codes.matches(user, "01234568", hash)).isFalse();
        assertThatThrownBy(() -> codes.hash(user, "１２３４５６７８")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadOrReusedKeysAndRedactsSensitiveRepresentations() throws Exception {
        var jwt = jwt();
        for (String key : List.of("invalid-base64", Base64.getEncoder().encodeToString(new byte[31]), jwt.secret())) {
            assertThatThrownBy(
                    () -> new EmailVerificationCodeService(new EmailVerificationProperties(key, 100, 10), jwt))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(key);
        }
        assertThat(properties().toString()).doesNotContain(properties().secret());
        assertThat(new ConfirmEmailRequest("01234567").toString()).doesNotContain("01234567");
        assertThat(new EmailVerificationRequested("private@example.org", "01234567").toString())
                .doesNotContain("private@example.org", "01234567");
        assertThat(new EmailMessage("private@example.org", "subject", "01234567", "01234567").toString())
                .doesNotContain("private@example.org", "01234567");
    }
}
