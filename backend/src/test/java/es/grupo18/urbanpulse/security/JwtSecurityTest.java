package es.grupo18.urbanpulse.security;

import java.time.*;
import java.util.*;
import es.grupo18.urbanpulse.config.*;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.exception.AuthenticationBackendException;
import es.grupo18.urbanpulse.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtSecurityTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID id = UUID.randomUUID();
    private final SecurityConfig config = new SecurityConfig();
    private final JwtProperties properties = new JwtProperties("test-issuer",
            Base64.getEncoder().encodeToString(new byte[32]), 7200, List.of("http://localhost:7777"));
    private final JwtEncoder encoder = config.jwtEncoder(config.jwtKey(properties));
    private final JwtDecoder decoder = config.jwtDecoder(config.jwtKey(properties), properties,
            Clock.fixed(now, ZoneOffset.UTC));
    private final UserRepository users = mock(UserRepository.class);
    private final DatabaseJwtAuthenticationConverter converter = new DatabaseJwtAuthenticationConverter(users);

    private String token(Object subject, Object version, String issuer, Instant issued, Instant expires) {
        var claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(issued).expiresAt(expires)
                .claim("sub", subject).claim("ver", version).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void acceptsValidSignatureAndRejectsTamperingExpiryIssuerAndMalformedClaims() {
        String valid = token(id.toString(), 0L, "test-issuer", now, now.plusSeconds(7200));
        assertThat(decoder.decode(valid).getSubject()).isEqualTo(id.toString());
        String[] parts = valid.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A")
                + parts[2].substring(1);
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
        for (String invalid : List.of(
                token(id.toString(), 0, "other", now, now.plusSeconds(7200)),
                token(id.toString(), 0, "test-issuer", now.minusSeconds(8000), now.minusSeconds(1)),
                token(id.toString(), 0, "test-issuer", now.minusSeconds(7200), now),
                token("not-a-uuid", 0, "test-issuer", now, now.plusSeconds(7200)),
                token("1-1-1-1-1", 0, "test-issuer", now, now.plusSeconds(7200)),
                token(id.toString(), "0", "test-issuer", now, now.plusSeconds(7200)),
                token(id.toString(), 0.5, "test-issuer", now, now.plusSeconds(7200)),
                token(id.toString(), -1, "test-issuer", now, now.plusSeconds(7200)),
                token(id.toString(), true, "test-issuer", now, now.plusSeconds(7200)),
                token(id.toString(), 0, "test-issuer", now.plusSeconds(10), now.plusSeconds(7200)))) {
            assertThatThrownBy(() -> decoder.decode(invalid)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void rejectsMissingClaimsAndBadKeyConfiguration() {
        var claims = JwtClaimsSet.builder().issuer("test-issuer").subject(id.toString()).issuedAt(now).build();
        String missing = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        assertThatThrownBy(() -> decoder.decode(missing)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> new JwtProperties("x", "placeholder", 7200, List.of("x")).keyBytes())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsFreshRolesEveryTimeAndFailsClosed() {
        Jwt jwt = decoder.decode(token(id.toString(), 0, "test-issuer", now, now.plusSeconds(7200)));
        var row = mock(UserRepository.SecurityState.class);
        when(row.getActive()).thenReturn(true);
        when(row.getRoleCode()).thenReturn(RoleCode.CITIZEN);
        var admin = mock(UserRepository.SecurityState.class);
        when(admin.getRoleCode()).thenReturn(RoleCode.ADMIN);
        when(users.findSecurityState(id)).thenReturn(List.of(row, admin)).thenReturn(List.of(row));
        assertThat(converter.convert(jwt).getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_CITIZEN");
        assertThat(converter.convert(jwt).getAuthorities()).extracting("authority").containsExactly("ROLE_CITIZEN");
        when(row.getActive()).thenReturn(false);
        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
        when(row.getActive()).thenReturn(true);
        when(row.getTokenVersion()).thenReturn(1L);
        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
        when(users.findSecurityState(id)).thenReturn(List.of());
        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
        when(users.findSecurityState(id)).thenThrow(new DataAccessResourceFailureException("not exposed"));
        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOfSatisfying(AuthenticationBackendException.class, error -> {
                    assertThat(error.isUnavailable()).isTrue();
                    assertThat(error.getCause()).isNull();
                    assertThat(error.getMessage()).doesNotContain("not exposed");
                });
        verify(users, times(6)).findSecurityState(id);
    }
}
