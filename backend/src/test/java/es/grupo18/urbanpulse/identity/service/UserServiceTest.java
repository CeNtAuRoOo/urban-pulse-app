
package es.grupo18.urbanpulse.identity.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.grupo18.urbanpulse.identity.dto.AdminUpdateUserRequest;
import es.grupo18.urbanpulse.identity.dto.UpdateMyProfileRequest;
import es.grupo18.urbanpulse.identity.dto.UserDTO;
import es.grupo18.urbanpulse.identity.dto.UsersPageDTO;
import es.grupo18.urbanpulse.identity.model.Role;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.organization.model.Department;
import es.grupo18.urbanpulse.organization.model.DepartmentMembership;
import es.grupo18.urbanpulse.organization.repository.DepartmentMembershipRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private DepartmentMembershipRepository memberships;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = mock(User.class);
    }

    private void configureUser() {
        when(user.getId()).thenReturn(userId);
        when(user.getDisplayName()).thenReturn("Usuario Prueba");
        when(user.getEmail()).thenReturn("prueba@urbanpulse.com");
        when(user.isEmailVerified()).thenReturn(true);
        when(user.isActive()).thenReturn(true);
        when(user.getCreatedAt()).thenReturn(Instant.now());
        when(user.globalRoles()).thenReturn(Set.of(RoleCode.CITIZEN));
    }

    @Test
    void getMeReturnsUserProfile() {
        configureUser();

        when(users.findById(userId)).thenReturn(Optional.of(user));

        UserDTO result = userService.getMe(userId);

        assertNotNull(result);
        assertEquals(userId, result.id());
        assertEquals("Usuario Prueba", result.displayName());
        assertEquals("prueba@urbanpulse.com", result.email());
        assertTrue(result.emailVerified());
        assertTrue(result.active());
        assertEquals(Set.of(RoleCode.CITIZEN), result.roles());

        verify(users).findById(userId);
    }

    @Test
    void getMeThrowsWhenUserDoesNotExist() {
        when(users.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> userService.getMe(userId));

        verify(users).findById(userId);
    }

    @Test
    void updateMeChangesDisplayName() {
        configureUser();

        when(users.findForEmailVerification(userId)).thenReturn(Optional.of(user));

        UpdateMyProfileRequest request = new UpdateMyProfileRequest("Nuevo Nombre");

        UserDTO result = userService.updateMe(userId, request);

        verify(user).updateDisplayName("Nuevo Nombre");

        assertNotNull(result);
        assertEquals("Usuario Prueba", result.displayName());
    }

    @Test
    void updateMeThrowsWhenUserDoesNotExist() {
        when(users.findForEmailVerification(userId)).thenReturn(Optional.empty());

        UpdateMyProfileRequest request = new UpdateMyProfileRequest("Nuevo Nombre");

        assertThrows(
                EntityNotFoundException.class,
                () -> userService.updateMe(userId, request));

        verify(user, never()).updateDisplayName(anyString());
    }

    @Test
    void getUserByIdReturnsUser() {
        configureUser();

        when(users.findUserWithRolesById(userId))
                .thenReturn(Optional.of(user));

        UserDTO result = userService.getUserById(userId);

        assertNotNull(result);
        assertEquals(userId, result.id());
        assertEquals("Usuario Prueba", result.displayName());

        verify(users).findUserWithRolesById(userId);
    }

    @Test
    void getUserByIdThrowsWhenUserDoesNotExist() {
        when(users.findUserWithRolesById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> userService.getUserById(userId));
    }

    @Test
    void updateUserChangesActiveStatus() {
        configureUser();

        when(users.findForEmailVerification(userId))
                .thenReturn(Optional.of(user));

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(false);

        UserDTO result = userService.updateUser(userId, request);

        verify(user).setActive(false);

        assertNotNull(result);
        assertTrue(result.active());
    }

    @Test
    void updateUserThrowsWhenUserDoesNotExist() {
        when(users.findForEmailVerification(userId))
                .thenReturn(Optional.empty());

        AdminUpdateUserRequest request = new AdminUpdateUserRequest(false);

        assertThrows(
                EntityNotFoundException.class,
                () -> userService.updateUser(userId, request));

        verify(user, never()).setActive(anyBoolean());
    }

    @Test
    void getUsersReturnsPaginatedResults() {
        configureUser();

        Page<User> page = new PageImpl<>(
                List.of(user),
                PageRequest.of(0, 20),
                1);

        when(users.searchUsers(
                isNull(),
                isNull(),
                any(Pageable.class)))
                .thenReturn(page);

        UsersPageDTO result = userService.getUsers(
                1, 20, null, null);

        assertNotNull(result);
        assertEquals(1, result.data().size());
        assertEquals(userId, result.data().get(0).id());

        assertEquals(1, result.pagination().page());
        assertEquals(20, result.pagination().pageSize());
        assertEquals(1, result.pagination().totalItems());
        assertEquals(1, result.pagination().totalPages());

        verify(users).searchUsers(
                isNull(),
                isNull(),
                any(Pageable.class));
    }

    @Test
    void getUsersPassesFiltersAndPagination() {
        configureUser();

        Page<User> page = new PageImpl<>(
                List.of(user),
                PageRequest.of(1, 10),
                11);

        when(users.searchUsers(
                eq(RoleCode.CITIZEN),
                eq(true),
                any(Pageable.class)))
                .thenReturn(page);

        UsersPageDTO result = userService.getUsers(
                2, 10, RoleCode.CITIZEN, true);

        assertEquals(2, result.pagination().page());
        assertEquals(10, result.pagination().pageSize());
        assertEquals(11, result.pagination().totalItems());
        assertEquals(2, result.pagination().totalPages());

        verify(users).searchUsers(
                eq(RoleCode.CITIZEN),
                eq(true),
                argThat(pageable -> pageable.getPageNumber() == 1
                        && pageable.getPageSize() == 10
                        && pageable.getSort()
                                .getOrderFor("createdAt")
                                .getDirection() == Sort.Direction.DESC
                        && pageable.getSort()
                                .getOrderFor("id")
                                .getDirection() == Sort.Direction.ASC));
    }

    /**
     * Comprueba que el perfil del usuario incluye sus pertenencias departamentales
     * y los datos de departamento y rol asociados.
     */
    @Test
    void getMeIncludesDepartmentMemberships() {
        configureUser();

        UUID departmentId = UUID.randomUUID();
        Instant joinedAt = Instant.parse("2026-01-10T09:00:00Z");
        Department department = mock(Department.class);
        Role role = mock(Role.class);
        DepartmentMembership membership = mock(DepartmentMembership.class);

        when(department.getId()).thenReturn(departmentId);
        when(department.getName()).thenReturn("Mantenimiento Urbano");
        when(department.getCode()).thenReturn("MANT");
        when(department.getDescription()).thenReturn("Conservación urbana.");
        when(department.isActive()).thenReturn(true);
        when(role.getCode()).thenReturn(RoleCode.TECHNICIAN);
        when(membership.getUser()).thenReturn(user);
        when(membership.getDepartment()).thenReturn(department);
        when(membership.getRole()).thenReturn(role);
        when(membership.getJoinedAt()).thenReturn(joinedAt);
        when(membership.getLeftAt()).thenReturn(null);

        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(memberships.findAllForUsers(List.of(userId))).thenReturn(List.of(membership));

        UserDTO result = userService.getMe(userId);

        assertEquals(1, result.departmentMemberships().size());
        assertEquals(RoleCode.TECHNICIAN, result.departmentMemberships().getFirst().role());
        assertEquals("MANT", result.departmentMemberships().getFirst().department().code());
        assertEquals(joinedAt, result.departmentMemberships().getFirst().joinedAt());
        assertEquals(null, result.departmentMemberships().getFirst().leftAt());
        verify(memberships).findAllForUsers(List.of(userId));
    }
}
