package es.grupo18.urbanpulse.identity.dto;

import java.util.UUID;

import es.grupo18.urbanpulse.identity.model.User;

/**
 * Identidad pública mínima compartida; no contiene email, roles ni
 * pertenencias.
 *
 * @param id          identidad estable
 * @param displayName nombre visible actual
 */
public record UserSummaryDTO(UUID id, String displayName) {
    /**
     * Proyecta el nombre vigente sin datos de cuenta ni permisos.
     *
     * @param user usuario inicializado
     * @return resumen mínimo, o null para un actor de sistema
     */
    public static UserSummaryDTO from(User user) {
        return user == null ? null : new UserSummaryDTO(user.getId(), user.getDisplayName());
    }
}
