package es.grupo18.urbanpulse.categories.dto;

import java.util.UUID;

/**
 * Proyección pública del catálogo, independiente de JPA.
 *
 * @param id          identidad UUIDv7 estable
 * @param name        nombre recortado y único sin distinguir mayúsculas
 * @param description texto opcional; la clave se serializa incluso cuando vale
 *                    null
 * @param active      estado que permite desactivar sin eliminar
 */
public record CategoryResponse(UUID id, String name, String description, boolean active) {
}
