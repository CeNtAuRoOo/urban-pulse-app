package es.grupo18.urbanpulse.dto;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description, boolean active) {
}
