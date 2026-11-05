package es.grupo18.urbanpulse.invitations.service;

import java.time.*;
import java.util.*;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.auth.config.EmailVerificationProperties;
import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.invitations.config.StaffInvitationProperties;
import es.grupo18.urbanpulse.invitations.dto.InvitationRedemptionRequest;
import es.grupo18.urbanpulse.invitations.mail.StaffInvitationEmailListener;
import es.grupo18.urbanpulse.invitations.mail.StaffInvitationRequested;
import es.grupo18.urbanpulse.invitations.model.InvitationStatus;
import es.grupo18.urbanpulse.invitations.model.StaffInvitation;
import es.grupo18.urbanpulse.shared.mail.EmailMessage;
import es.grupo18.urbanpulse.shared.mail.EmailSender;
import es.grupo18.urbanpulse.shared.security.JwtProperties;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvitationCodeServiceTest {
    private final String jwtKey = Base64.getEncoder().encodeToString(new byte[32]);
    private final String emailKey = Base64.getEncoder().encodeToString("1".repeat(32).getBytes());
    private final String invitationKey = Base64.getEncoder().encodeToString("2".repeat(32).getBytes());
    private final JwtProperties jwt = new JwtProperties("tests", jwtKey, 7200, List.of("http://localhost"));
    private final EmailVerificationProperties verification = new EmailVerificationProperties(emailKey, 100, 10);

    @Test
    void generatesCanonical256BitSecretsAndDeterministicIndependentHmac() {
        var codes = new InvitationCodeService(new StaffInvitationProperties(invitationKey, 7), jwt, verification);
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = codes.generate();
            assertThat(codes.validFormat(code)).isTrue();
            assertThat(code).hasSize(43);
            assertThat(Base64.getUrlDecoder().decode(code)).hasSize(32);
            assertThat(codes.hash(code)).hasSize(32).isEqualTo(codes.hash(code));
            assertThat(generated.add(code)).isTrue();
        }
        assertThat(codes.hash(generated.iterator().next())).isNotEqualTo(codes.hash(codes.generate()));
        for (String bad : List.of("12345678", "x".repeat(43), "A".repeat(44), "A".repeat(43) + "=")) {
            assertThat(codes.validFormat(bad)).isFalse();
            assertThatThrownBy(() -> codes.hash(bad)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Formato de invitación no válido.");
        }
        assertThat(codes.validFormat(null)).isFalse();
    }

    @Test
    void validatesKeySeparationLengthAndConfiguredLifetime() {
        for (String bad : List.of(jwtKey, emailKey, "not-base64", "AA==")) {
            assertThatThrownBy(
                    () -> new InvitationCodeService(new StaffInvitationProperties(bad, 7), jwt, verification))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new StaffInvitationProperties(invitationKey, 0))).isNotEmpty();
            assertThat(validator.validate(new StaffInvitationProperties(invitationKey, 31))).isNotEmpty();
            assertThat(validator.validate(new StaffInvitationProperties(invitationKey, 7))).isEmpty();
        }
        assertThat(new StaffInvitationProperties(invitationKey, 7).toString()).doesNotContain(invitationKey);
    }

    @Test
    void stateIsDerivedAtExactBoundaryAndFinalStatesAreExclusive() {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(RoleCode.ADMIN);
        User actor = mock(User.class);
        Instant now = Instant.parse("2026-10-30T12:00:00Z");
        Instant expires = now.plus(Duration.ofDays(7));
        var invitation = new StaffInvitation(new byte[32], " PERSON@EXAMPLE.ORG ", role, null, actor, now, expires);
        assertThat(invitation.getEmail()).isEqualTo("person@example.org");
        assertThat(invitation.statusAt(expires.minusNanos(1))).isEqualTo(InvitationStatus.ACTIVE);
        assertThat(invitation.statusAt(expires)).isEqualTo(InvitationStatus.EXPIRED);
        assertThatThrownBy(() -> invitation.redeem(actor, expires)).isInstanceOf(IllegalStateException.class);
        invitation.revoke(actor, now.plusSeconds(1));
        assertThat(invitation.statusAt(expires)).isEqualTo(InvitationStatus.REVOKED);
        assertThatThrownBy(() -> invitation.redeem(actor, now.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretBearingObjectsAreRedactedAndMailHtmlIsEscaped() {
        String code = "A".repeat(43);
        var event = new StaffInvitationRequested("private@example.org", code, RoleCode.TECHNICIAN,
                "<script>&\"", Instant.parse("2026-11-06T12:00:00Z"));
        assertThat(event.toString()).doesNotContain(code, "private@example.org");
        assertThat(new InvitationRedemptionRequest(code).toString()).doesNotContain(code);
        var mapper = JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(event)).doesNotContain(code, "private@example.org");
        assertThat(mapper.writeValueAsString(new InvitationRedemptionRequest(code))).doesNotContain(code);
        EmailSender sender = mock(EmailSender.class);
        new StaffInvitationEmailListener(sender).send(event);
        var mail = org.mockito.ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender).send(mail.capture());
        assertThat(mail.getValue().text()).contains(code, "regístrate", "verifica", "inicia sesión", "2026-11-06");
        assertThat(mail.getValue().html()).contains("&lt;script&gt;&amp;&quot;").doesNotContain("<script>");
        assertThat(mail.getValue().toString()).doesNotContain(code, "private@example.org");
    }
}
