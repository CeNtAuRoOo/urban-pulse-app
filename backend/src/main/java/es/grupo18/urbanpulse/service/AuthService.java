package es.grupo18.urbanpulse.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import es.grupo18.urbanpulse.dto.auth.*;
import es.grupo18.urbanpulse.dto.user.UserDTO;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.entity.model.*;
import es.grupo18.urbanpulse.exception.ApiException;
import es.grupo18.urbanpulse.repository.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

/**
 * Servicio encargado de gestionar la autenticación de usuarios.
 * Implementa el registro, inicio de sesión y cambio de contraseña,
 * incluyendo el cifrado de credenciales, asignación de roles e invalidación de
 * JWT.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final TransactionTemplate transactions;
    private final String dummyHash;

    public AuthService(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
            PasswordEncoder passwords, JwtTokenService tokens, PlatformTransactionManager manager) {

        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.passwords = passwords;
        this.tokens = tokens;
        this.transactions = new TransactionTemplate(manager);
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    /**
     * Registra un nuevo usuario y le asigna el rol global CITIZEN
     * dentro de una transacción, devolviendo un JWT de acceso.
     *
     * @param request datos de registro del nuevo usuario
     * @return respuesta con el token JWT y los datos públicos del usuario
     * @throws ApiException si el correo ya está registrado o la contraseña supera
     *                      el límite permitido
     */
    public TokenDTO register(RegisterRequest request) {
        checkBcryptLength(request.password());
        String hash = passwords.encode(request.password());

        try {
            User user = transactions.execute(status -> {
                User created = users.saveAndFlush(new User(request.displayName(), request.email(), hash));
                Role citizen = roles.findById(RoleCode.CITIZEN).orElseThrow();
                userRoles.saveAndFlush(new UserRole(created, citizen));
                return created;
            });

            return tokens.issue(user, UserDTO.from(user, Set.of(RoleCode.CITIZEN)));
        }

        catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException constraint
                        && "users_email_unique".equals(constraint.getConstraintName())) {
                    throw new ApiException(HttpStatus.CONFLICT, "EMAIL_CONFLICT", "El correo ya está registrado.");
                }
            }

            throw ex;
        }
    }

    /**
     * Autentica a un usuario comprobando sus credenciales y el estado de su cuenta.
     * Utiliza un hash ficticio cuando el correo no existe para reducir diferencias
     * en los tiempos de respuesta y devuelve un JWT si la autenticación es válida.
     *
     * @param request credenciales de inicio de sesión
     * @return respuesta con el token JWT y los datos públicos del usuario
     * @throws ApiException si las credenciales no son válidas o la cuenta está
     *                      inactiva
     */
    @Transactional(readOnly = true)
    public TokenDTO login(LoginRequest request) {
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        boolean matches = matches(request.password(), user == null ? dummyHash : user.getPasswordHash());

        if (user == null || !matches || !user.isActive()) {
            throw ApiException.unauthorized();
        }

        return tokens.issue(user, UserDTO.from(user, user.globalRoles()));
    }

    /**
     * Cambia la contraseña de un usuario tras verificar su contraseña actual
     * y la versión del JWT, utilizando un bloqueo pesimista para evitar
     * conflictos concurrentes e invalidando los tokens anteriores.
     *
     * @param id               identificador del usuario
     * @param presentedVersion versión del JWT utilizado en la petición
     * @param request          datos necesarios para cambiar la contraseña
     * @throws ApiException si la autenticación no es válida o la nueva contraseña
     *                      supera el límite permitido
     */
    @Transactional
    public void changePassword(UUID id, long presentedVersion, ChangePasswordRequest request) {
        User user = users.findForPasswordChange(id).orElseThrow(ApiException::unauthorized);
        if (!user.isActive() || user.getTokenVersion() != presentedVersion) {
            throw ApiException.unauthorized();
        }

        if (!matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized();
        }

        checkBcryptLength(request.newPassword());
        user.changePassword(passwords.encode(request.newPassword()));
        users.flush();
    }

    /**
     * Comprueba si una contraseña coincide con su hash BCrypt.
     * Rechaza contraseñas que superan los 72 bytes, ejecutando igualmente
     * una comparación ficticia para reducir diferencias temporales.
     *
     * @param raw  contraseña en texto plano
     * @param hash hash BCrypt contra el que se realiza la comprobación
     * @return true si la contraseña coincide y tiene una longitud válida
     */
    private boolean matches(String raw, String hash) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            passwords.matches("invalid-overlong-password", dummyHash);
            return false;
        }

        return passwords.matches(raw, hash);
    }

    /**
     * Comprueba que una contraseña no supere los 72 bytes en UTF-8,
     * límite de longitud admitido por la implementación de BCrypt.
     *
     * @param raw contraseña en texto plano que se desea validar
     * @throws ApiException si la contraseña supera los 72 bytes
     */
    private static void checkBcryptLength(String raw) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "PASSWORD_LENGTH",
                    "La contraseña no puede superar 72 bytes UTF-8.");
        }
    }
}
