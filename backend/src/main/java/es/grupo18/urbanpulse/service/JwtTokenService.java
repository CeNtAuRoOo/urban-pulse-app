package es.grupo18.urbanpulse.service;

import java.time.*;
import es.grupo18.urbanpulse.config.JwtProperties;
import es.grupo18.urbanpulse.dto.auth.TokenDTO;
import es.grupo18.urbanpulse.dto.user.UserDTO;
import es.grupo18.urbanpulse.entity.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado de generar y firmar tokens JWT de acceso.
 * Utiliza la configuración de seguridad para establecer su emisor,
 * duración y firma mediante el algoritmo HS256.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    /**
     * Genera un JWT firmado con la identidad del usuario, las fechas de emisión
     * y expiración, y la versión del token utilizada para su invalidación.
     *
     * @param user entidad del usuario autenticado
     * @param dto  datos públicos del usuario que se incluirán en la respuesta
     * @return respuesta con el JWT, su duración y los datos del usuario
     */
    public TokenDTO issue(User user, UserDTO dto) {
        Instant now = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString()).issuedAt(now)
                .expiresAt(now.plusSeconds(properties.ttlSeconds()))
                .claim("ver", user.getTokenVersion())
                .build();

        String token = encoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new TokenDTO(token, "Bearer", properties.ttlSeconds(), dto);
    }
}
