package es.grupo18.urbanpulse.security;

import java.util.*;
import es.grupo18.urbanpulse.entity.enums.RoleCode;
import es.grupo18.urbanpulse.exception.AuthenticationBackendException;
import es.grupo18.urbanpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Convierte un JWT validado en una autenticación de Spring Security.
 * Comprueba el estado actual del usuario y sus permisos en la base de datos,
 * sin confiar en los roles incluidos en el propio token.
 */
@Component
@RequiredArgsConstructor
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    /**
     * Extrae y valida el identificador del usuario contenido en el claim sub.
     *
     * @param jwt token JWT del que se obtiene el identificador
     * @return identificador UUID del usuario
     * @throws InvalidBearerTokenException si el identificador no es válido
     */
    public static UUID subject(Jwt jwt) {
        Object sub = jwt.getClaims().get("sub");

        if (!(sub instanceof String text)) {
            throw invalid();
        }

        try {
            UUID id = UUID.fromString(text);

            if (!id.toString().equalsIgnoreCase(text)) {
                throw invalid();
            }

            return id;
        }

        catch (IllegalArgumentException ex) {
            throw invalid();
        }
    }

    /**
     * Extrae y valida la versión del token almacenada en el claim ver.
     *
     * @param jwt token JWT del que se obtiene la versión
     * @return versión no negativa del token
     * @throws InvalidBearerTokenException si la versión no es válida
     */
    public static long version(Jwt jwt) {
        Object version = jwt.getClaims().get("ver");

        if (!(version instanceof Long || version instanceof Integer) || ((Number) version).longValue() < 0) {
            throw invalid();
        }

        return ((Number) version).longValue();
    }

    /**
     * Autentica al usuario a partir de un JWT previamente validado.
     * Consulta su estado, comprueba la versión del token y obtiene
     * los roles globales vigentes desde la base de datos, exigiendo CITIZEN.
     *
     * @param jwt token JWT validado
     * @return autenticación con la identidad y los permisos actuales del usuario
     * @throws InvalidBearerTokenException si el usuario, el token o el rol base no son válidos
     * @throws AuthenticationBackendException si falla el backend de autenticación
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID id = subject(jwt);
        long version = version(jwt);

        try {
            var rows = users.findSecurityState(id);

            if (rows.isEmpty() || !rows.getFirst().getActive() || rows.getFirst().getTokenVersion() != version) {
                throw invalid();
            }

            if (rows.stream().noneMatch(row -> row.getRoleCode() == RoleCode.CITIZEN)) {
                throw invalid();
            }

            var authorities = rows.stream()
                    .map(row -> row.getRoleCode())
                    .filter(Objects::nonNull).filter(code -> code.isGlobal())
                    .map(code -> new SimpleGrantedAuthority("ROLE_" + code.name())).distinct().toList();

            // TODO ADR-0009: resource ownership and departmental authorization in their own
            // tags.

            return new JwtAuthenticationToken(jwt, authorities, id.toString());
        }

        catch (InvalidBearerTokenException ex) {
            throw ex;
        }

        catch (RuntimeException ex) {
            throw new AuthenticationBackendException(ex);
        }
    }

    /**
     * Crea una excepción de autenticación para indicar que el JWT
     * o las credenciales asociadas no son válidos.
     *
     * @return excepción de token Bearer no válido
     */
    private static InvalidBearerTokenException invalid() {
        return new InvalidBearerTokenException("Autenticación no válida.");
    }
}
