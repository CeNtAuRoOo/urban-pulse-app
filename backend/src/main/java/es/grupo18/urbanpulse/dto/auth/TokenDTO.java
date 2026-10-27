package es.grupo18.urbanpulse.dto.auth;

import es.grupo18.urbanpulse.dto.user.UserDTO;

/**
 * Representa la respuesta de autenticación, incluyendo el token JWT
 * y los datos públicos del usuario autenticado.
 *
 * @param accessToken token JWT de acceso
 * @param tokenType   tipo de autenticación utilizado (Bearer)
 * @param expiresIn   tiempo de validez del token en segundos
 * @param user        datos públicos del usuario autenticado
 */
public record TokenDTO(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserDTO user) {

    /**
     * Devuelve una representación del DTO sin revelar el token de acceso.
     *
     * @return representación textual con los datos sensibles ocultos
     */
    @Override
    public String toString() {
        return "TokenDTO[REDACTED]";
    }
}
