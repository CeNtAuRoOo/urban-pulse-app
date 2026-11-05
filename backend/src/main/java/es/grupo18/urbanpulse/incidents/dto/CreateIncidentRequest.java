package es.grupo18.urbanpulse.incidents.dto;

import java.util.UUID;

/**
 * Alta estricta; titular, estado y prioridad nunca se reciben del cliente.
 * 
 * @param title       título de 5–150 puntos Unicode tras trim
 * @param description descripción no blanca de 10–5000 puntos Unicode tras trim
 * @param coordinates posición obligatoria, validada por el dominio
 * @param categoryId  categoría activa opcional; omisión/null equivale a sin
 *                    categoría
 */
public record CreateIncidentRequest(String title, String description, CoordinatesDTO coordinates, UUID categoryId) {
}
