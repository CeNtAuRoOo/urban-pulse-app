package es.grupo18.urbanpulse.incidents.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.http.HttpStatus;

import org.hibernate.annotations.UuidGenerator;

import es.grupo18.urbanpulse.categories.model.Category;
import es.grupo18.urbanpulse.identity.model.User;
import es.grupo18.urbanpulse.organization.model.Department;
import es.grupo18.urbanpulse.shared.error.ApiException;

/**
 * Reporte con titular inmutable, corrección limitada a REPORTED y versión
 * optimista. Las fechas del núcleo se truncan a microsegundos antes de persistir
 * para que POST/PATCH y la lectura PostgreSQL representen los mismos instantes.
 */
@Entity
@Table(name = "incidents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Incident {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false, updatable = false)
    private User reporter;
    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;
    @ManyToOne(fetch = FetchType.LAZY)
    private Department assignedDepartment;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, length = 5000)
    private String description;
    @Embedded
    private Location location;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private IncidentPriority priority;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant resolvedAt;
    private Instant closedAt;
    @Version
    @Column(nullable = false)
    private long version;

    /**
     * Construye un reporte; no crea revisión, prioridad ni transición inicial.
     * 
     * @param reporter    titular autenticado
     * @param title       título que se normaliza y valida
     * @param description descripción que se normaliza y valida
     * @param category    categoría activa validada por el servicio, o null
     * @param location    posición validada
     * @param now         instante UTC del registro
     */
    public Incident(User reporter, String title, String description, Category category, Location location,
            Instant now) {
        this.reporter = Objects.requireNonNull(reporter);
        this.title = normalizeText(title, 5, 150);
        this.description = normalizeText(description, 10, 5000);
        this.category = category;
        this.location = Objects.requireNonNull(location);
        this.status = IncidentStatus.REPORTED;
        this.createdAt = Objects.requireNonNull(now).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.updatedAt = this.createdAt;
    }

    /**
     * Corrige los valores efectivos; el servicio ya resolvió campos omitidos y
     * permisos.
     * 
     * @param title       título efectivo
     * @param description descripción efectiva
     * @param category    clasificación efectiva, que puede ser null
     * @param location    posición completa efectiva
     * @param now         instante de la modificación
     * @return true solo si cambió algún valor; un no-op no altera fecha ni versión
     * @throws ApiException si el estado no permite corregir (409) o el texto es
     *                      inválido (422)
     */
    public boolean correct(String title, String description, Category category, Location location, Instant now) {
        if (status != IncidentStatus.REPORTED) {
            throw new ApiException(HttpStatus.CONFLICT, "INCIDENT_NOT_REPORTED",
                    "Solo se pueden corregir reportes iniciales.");
        }
        String nextTitle = normalizeText(title, 5, 150);
        String nextDescription = normalizeText(description, 10, 5000);
        Objects.requireNonNull(location);
        if (this.title.equals(nextTitle) && this.description.equals(nextDescription)
                && Objects.equals(categoryId(this.category), categoryId(category)) && this.location.equals(location)) {
            return false;
        }
        this.title = nextTitle;
        this.description = nextDescription;
        this.category = category;
        this.location = location;
        this.updatedAt = now.isBefore(createdAt) ? createdAt : now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return true;
    }

    /** @return identidad del catálogo sin inicializar sus relaciones */
    private static UUID categoryId(Category category) {
        return category == null ? null : category.getId();
    }

    /**
     * Aplica trim de Java y cuenta puntos Unicode; conserva formato interior y
     * acentos.
     * Rechaza NUL y sustitutos aislados, que PostgreSQL UTF-8 no puede representar.
     * 
     * @param value texto recibido
     * @param min   mínimo normalizado
     * @param max   máximo normalizado
     * @return texto normalizado
     * @throws ApiException ante texto nulo, blanco o fuera de límites (422)
     */
    public static String normalizeText(String value, int min, int max) {
        String text = value == null ? "" : value.trim();
        int length = text.codePointCount(0, text.length());
        if (text.isBlank() || length < min || length > max
                || text.codePoints().anyMatch(c -> c == 0 || (c >= 0xD800 && c <= 0xDFFF))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                    "Texto fuera de los límites admitidos.");
        }
        return text;
    }
}
