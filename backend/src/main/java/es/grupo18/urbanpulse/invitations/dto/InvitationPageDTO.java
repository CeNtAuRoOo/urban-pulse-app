package es.grupo18.urbanpulse.invitations.dto;

import java.util.List;

import es.grupo18.urbanpulse.shared.pagination.PageMetadata;

/**
 * Página administrativa sin secretos.
 *
 * @param data       resultados ordenados
 * @param pagination metadatos compartidos
 */
public record InvitationPageDTO(
        List<InvitationDTO> data,
        PageMetadata pagination) {
}
