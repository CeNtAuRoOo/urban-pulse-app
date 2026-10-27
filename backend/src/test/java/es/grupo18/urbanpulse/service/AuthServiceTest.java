package es.grupo18.urbanpulse.service;

import java.time.*;
import java.util.*;
import es.grupo18.urbanpulse.config.*;
import es.grupo18.urbanpulse.dto.auth.*;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.*;
import es.grupo18.urbanpulse.exception.ApiException;
import es.grupo18.urbanpulse.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserRoleRepository assignments = mock(UserRoleRepository.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final JwtProperties props = new JwtProperties("unit", Base64.getEncoder().encodeToString(new byte[32]),
            7200, List.of("http://localhost:7777"));
    private final JwtTokenService tokens = new JwtTokenService(
            new SecurityConfig().jwtEncoder(new SecurityConfig().jwtKey(props)), props, Clock.systemUTC());
    private AuthService auth;

    @BeforeEach
    void setup() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        auth = new AuthService(users, roles, assignments, passwords, tokens, manager);
    }

    private User account() {
        User user = new User("Ciudadana", " Test@Example.org ", passwords.encode("password-123"));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", Instant.now());
        return user;
    }

    @Test
    void registerNormalizesHashesAssignsOnlyCitizenAndReturnsSafeDto() {
        Role citizen = mock(Role.class);
        when(citizen.getCode()).thenReturn(RoleCode.CITIZEN);
        when(roles.findById(RoleCode.CITIZEN)).thenReturn(Optional.of(citizen));
        when(users.saveAndFlush(any())).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(u, "createdAt", Instant.now());
            return u;
        });
        TokenDTO dto = auth.register(new RegisterRequest("Ciudadana", " A@Example.org ", "password-123"));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("a@example.org");
        assertThat(passwords.matches("password-123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        assertThat(saved.getValue().getTokenVersion()).isZero();
        assertThat(saved.getValue().getPreferences().isInApp()).isTrue();
        assertThat(saved.getValue().getPreferences().isEmail()).isTrue();
        assertThat(saved.getValue().getPreferences().isPush()).isTrue();
        ArgumentCaptor<UserRole> assignment = ArgumentCaptor.forClass(UserRole.class);
        verify(assignments).saveAndFlush(assignment.capture());
        assertThat(assignment.getValue().getId().getRoleCode()).isEqualTo(RoleCode.CITIZEN);
        verify(manager).commit(any());
        assertThat(dto.expiresIn()).isEqualTo(7200);
        assertThat(dto.user().roles()).containsExactly(RoleCode.CITIZEN);
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(dto);
        assertThat(json).doesNotContain("passwordHash", "tokenVersion", "departmentMemberships", "password-123");
        assertThat(dto.toString()).doesNotContain(dto.accessToken());
    }

    @Test
    void loginFailuresAreIndistinguishableAndUnverifiedUserCanLogin() {
        User user = account();
        when(users.findByEmail("test@example.org")).thenReturn(Optional.of(user));
        assertThat(auth.login(new LoginRequest(" TEST@EXAMPLE.ORG ", "password-123")).tokenType()).isEqualTo("Bearer");
        assertThatThrownBy(() -> auth.login(new LoginRequest("test@example.org", "bad")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Autenticación no válida.");
        ReflectionTestUtils.setField(user, "active", false);
        assertThatThrownBy(() -> auth.login(new LoginRequest("test@example.org", "password-123")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Autenticación no válida.");
        when(users.findByEmail("test@example.org")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.login(new LoginRequest("test@example.org", "password-123")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Autenticación no válida.");
    }

    @Test
    void passwordChangeChecksCurrentPasswordAndLockedVersionThenIncrements() {
        User user = account();
        when(users.findForPasswordChange(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> auth.changePassword(user.getId(), 0, new ChangePasswordRequest("bad", "password-456")))
                .isInstanceOf(ApiException.class);
        auth.changePassword(user.getId(), 0, new ChangePasswordRequest("password-123", "password-456"));
        assertThat(user.getTokenVersion()).isEqualTo(1);
        assertThat(passwords.matches("password-456", user.getPasswordHash())).isTrue();
        assertThatThrownBy(
                () -> auth.changePassword(user.getId(), 0, new ChangePasswordRequest("password-456", "password-789")))
                .isInstanceOf(ApiException.class);
        verify(users, times(1)).flush();
    }

    @Test
    void databaseQueryAndTransactionFailuresPropagateWithoutBecomingInvalidCredentials() {
        var unavailable = new DataAccessResourceFailureException("private connection");
        when(users.findByEmail(anyString())).thenThrow(unavailable);
        assertThatThrownBy(() -> auth.login(new LoginRequest("test@example.org", "password-123")))
                .isSameAs(unavailable);
        when(users.saveAndFlush(any())).thenThrow(unavailable);
        assertThatThrownBy(() -> auth.register(new RegisterRequest("Ciudadana", "test@example.org", "password-123")))
                .isSameAs(unavailable);
        verify(manager).rollback(any());
        var transactionFailure = new CannotCreateTransactionException("private transaction", unavailable);
        doThrow(transactionFailure).when(manager).getTransaction(any());
        assertThatThrownBy(() -> auth.register(new RegisterRequest("Ciudadana", "test@example.org", "password-123")))
                .isSameAs(transactionFailure);
        verifyNoInteractions(assignments);
    }

    @Test
    void rolesAndUtf8BcryptLimitAreEnforced() {
        for (RoleCode code : RoleCode.values()) {
            assertThat(code.isDepartmental()).isEqualTo(!code.isGlobal());
            Role role = mock(Role.class);
            when(role.getCode()).thenReturn(code);
            if (code.isDepartmental())
                assertThatThrownBy(() -> new UserRole(account(), role)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> auth.register(new RegisterRequest("Ciudadana", "test@example.org", "é".repeat(37))))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(users);
    }
}
