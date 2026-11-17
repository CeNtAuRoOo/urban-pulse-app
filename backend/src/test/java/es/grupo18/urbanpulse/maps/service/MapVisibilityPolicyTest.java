package es.grupo18.urbanpulse.maps.service;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.identity.repository.UserRepository.SecurityState;
import es.grupo18.urbanpulse.shared.error.ApiException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapVisibilityPolicyTest {
    private final UserRepository users = mock(UserRepository.class);
    private final MapVisibilityPolicy policy = new MapVisibilityPolicy(users);
    private final UUID id = UUID.randomUUID();

    @Test
    void revalidatesActiveVerifiedAccountTokenAndBaseRole() {
        when(users.findSecurityState(id)).thenReturn(List.of());
        denied(401);
        var state = row(RoleCode.CITIZEN);
        when(users.findSecurityState(id)).thenReturn(List.of(state));
        when(state.getActive()).thenReturn(false);
        denied(401);
        when(state.getActive()).thenReturn(true);
        when(state.getTokenVersion()).thenReturn(1L);
        denied(401);
        when(state.getTokenVersion()).thenReturn(0L);
        when(state.getEmailVerified()).thenReturn(false);
        denied(403);
        var admin = row(RoleCode.ADMIN);
        when(users.findSecurityState(id)).thenReturn(List.of(admin));
        denied(401);
    }

    @Test
    void onlyCurrentGlobalOperatorOrAdminGrantsGlobalMapAccess() {
        var citizen = row(RoleCode.CITIZEN);
        when(users.findSecurityState(id)).thenReturn(List.of(citizen));
        assertThat(policy.authorize(id, 0).global()).isFalse();
        for (RoleCode role : List.of(RoleCode.OPERATOR, RoleCode.ADMIN)) {
            var staff = row(role);
            when(users.findSecurityState(id)).thenReturn(List.of(citizen, staff));
            assertThat(policy.authorize(id, 0).global()).isTrue();
        }
        when(users.findSecurityState(id)).thenReturn(List.of(citizen));
        assertThat(policy.authorize(id, 0).global()).isFalse();
    }

    private SecurityState row(RoleCode role) {
        var state = mock(SecurityState.class);
        when(state.getActive()).thenReturn(true);
        when(state.getEmailVerified()).thenReturn(true);
        when(state.getTokenVersion()).thenReturn(0L);
        when(state.getRoleCode()).thenReturn(role);
        return state;
    }

    private void denied(int status) {
        assertThatThrownBy(() -> policy.authorize(id, 0)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status().value()).isEqualTo(status));
    }
}
