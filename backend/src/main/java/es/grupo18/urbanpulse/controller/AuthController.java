package es.grupo18.urbanpulse.controller;

import es.grupo18.urbanpulse.dto.auth.*;
import es.grupo18.urbanpulse.security.DatabaseJwtAuthenticationConverter;
import es.grupo18.urbanpulse.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST encargado de gestionar la autenticación de usuarios.
 * Expone los endpoints de registro, inicio de sesión y cambio de contraseña,
 * delegando su lógica de negocio en AuthService.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService auth;

    @PostMapping("/auth/register")
    public ResponseEntity<TokenDTO> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(request));
    }

    @PostMapping("/auth/login")
    public TokenDTO login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/users/me/password-changes")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {

        auth.changePassword(DatabaseJwtAuthenticationConverter.subject(jwt),
                DatabaseJwtAuthenticationConverter.version(jwt), request);

        return ResponseEntity.noContent().build();
    }
}
