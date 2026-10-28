package es.grupo18.urbanpulse.controller;

import es.grupo18.urbanpulse.dto.auth.*;
import es.grupo18.urbanpulse.security.DatabaseJwtAuthenticationConverter;
import es.grupo18.urbanpulse.service.AuthService;
import es.grupo18.urbanpulse.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST encargado de gestionar la autenticación y verificación
 * del correo electrónico de los usuarios.
 * Expone los endpoints de registro, inicio de sesión, cambio de contraseña,
 * confirmación del correo y solicitud de reenvío del código de verificación,
 * delegando su lógica de negocio en los servicios correspondientes.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService auth;
    private final EmailVerificationService verification;

    /**
     * Confirma el correo electrónico del usuario autenticado mediante
     * un código de verificación de ocho dígitos.
     * Devuelve HTTP 204 cuando la confirmación se realiza correctamente
     * o cuando la cuenta ya estaba verificada.
     *
     * @param jwt     token JWT del usuario autenticado
     * @param request código de verificación proporcionado por el usuario
     * @return respuesta HTTP 204 sin contenido
     * @throws es.grupo18.urbanpulse.exception.ApiException si el usuario
     *                                                      no está autorizado o el
     *                                                      código de verificación
     *                                                      no es válido
     */
    @PostMapping("/auth/email-verifications/confirm")
    public ResponseEntity<Void> confirmEmail(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConfirmEmailRequest request) {

        verification.confirm(DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt), request.code());

        return ResponseEntity.noContent().build();
    }

    /**
     * Solicita el reenvío de un nuevo código de verificación al correo
     * electrónico del usuario autenticado.
     * Devuelve HTTP 202 cuando se registra la solicitud de envío
     * y HTTP 204 cuando la cuenta ya estaba verificada.
     *
     * @param jwt token JWT del usuario autenticado
     * @return respuesta HTTP 202 o 204 sin contenido, según el estado
     *         de verificación de la cuenta
     * @throws es.grupo18.urbanpulse.exception.ApiException si el usuario
     *                                                      no está autorizado o la
     *                                                      cuenta pendiente ha
     *                                                      caducado
     */
    @PostMapping("/auth/email-verifications/resend")
    public ResponseEntity<Void> resendEmail(@AuthenticationPrincipal Jwt jwt) {
        boolean accepted = verification.resend(DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt));

        return accepted ? ResponseEntity.accepted().build() : ResponseEntity.noContent().build();
    }

    /**
     * Registra un nuevo usuario, asignándole el rol global CITIZEN
     * y creando su solicitud inicial de verificación del correo.
     *
     * @param request datos necesarios para registrar al nuevo usuario
     * @return respuesta HTTP 201 con el JWT y los datos públicos del usuario
     */
    @PostMapping("/auth/register")
    public ResponseEntity<TokenDTO> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(request));
    }

    /**
     * Autentica a un usuario mediante su correo electrónico y contraseña.
     * Permite iniciar sesión aunque el correo todavía no esté verificado.
     *
     * @param request credenciales de inicio de sesión
     * @return respuesta con el JWT y los datos públicos del usuario autenticado
     */
    @PostMapping("/auth/login")
    public TokenDTO login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /**
     * Cambia la contraseña del usuario autenticado tras comprobar
     * sus credenciales actuales y la versión del JWT.
     * Permite realizar la operación aunque el correo no esté verificado.
     *
     * @param jwt     token JWT del usuario autenticado
     * @param request contraseña actual y nueva contraseña del usuario
     * @return respuesta HTTP 204 sin contenido
     */
    @PostMapping("/users/me/password-changes")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {

        auth.changePassword(DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt), request);

        return ResponseEntity.noContent().build();
    }
}
