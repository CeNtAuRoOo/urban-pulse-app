package es.grupo18.urbanpulse.duplicates.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.grupo18.urbanpulse.audit.model.AuditEvent;
import es.grupo18.urbanpulse.audit.repository.AuditEventRepository;
import es.grupo18.urbanpulse.duplicates.dto.DuplicateLinkDTO;
import es.grupo18.urbanpulse.duplicates.dto.DuplicateLinkRequest;
import es.grupo18.urbanpulse.duplicates.dto.DuplicateSuggestionDTO;
import es.grupo18.urbanpulse.duplicates.model.DuplicateLink;
import es.grupo18.urbanpulse.duplicates.repository.DuplicateCandidateRepository;
import es.grupo18.urbanpulse.duplicates.repository.DuplicateLinkRepository;
import es.grupo18.urbanpulse.identity.dto.UserSummaryDTO;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.incidents.model.Incident;
import es.grupo18.urbanpulse.incidents.repository.IncidentRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

@Service
@RequiredArgsConstructor
public class DuplicateService {

    private final IncidentRepository incidents;
    private final DuplicateCandidateRepository candidates;
    private final DuplicateLinkRepository links;
    private final UserRepository users;
    private final AuditEventRepository audit;
    private final DuplicateScorer scorer;
    private final Clock clock;

    /** Obtiene candidatos recientes y cercanos; no persiste ni decide automáticamente. */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<DuplicateSuggestionDTO> candidates(UUID incidentId) {
        Incident source = incident(incidentId);
        double lat = source.getLocation().getCoordinates().getLatitude();
        double lon = source.getLocation().getCoordinates().getLongitude();
        String category = source.getCategory() == null ? null : source.getCategory().getName();
        try {
            return candidates.bestCandidates(source).stream().map(candidate -> {
                var score = scorer.score(lat, lon, source.getCreatedAt(), category,
                        candidate.latitude(), candidate.longitude(), candidate.createdAt(), candidate.category());
                return new DuplicateSuggestionDTO(candidate.id(), score.score(), score.reasons());
            }).toList();
        } catch (org.springframework.dao.QueryTimeoutException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DUPLICATE_QUERY_TIMEOUT",
                    "Consulta temporalmente no disponible. Vuelve a intentarlo.");
        }
    }

    /** Registra una relación simétrica y conserva intactos ambos reportes. */
    @Transactional
    public DuplicateLinkDTO link(UUID incidentId, UUID actorId, DuplicateLinkRequest request) {
        Incident first = incident(incidentId);
        Incident second = incident(request.candidateIncidentId());
        if (first.getId().equals(second.getId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "DUPLICATE_SELF_LINK",
                    "Una incidencia no puede vincularse consigo misma.");
        }
        if (links.existsBetween(first.getId(), second.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LINK_EXISTS",
                    "La relación entre estas incidencias ya existe.");
        }
        var actor = users.findUserWithRolesById(actorId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Autenticación no válida."));
        var link = new DuplicateLink(first, second, actor, clock.instant(), normalizeReason(request.reason()));
        try {
            link = links.saveAndFlush(link);
        } catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && "uk_duplicate_links_pair".equals(violation.getConstraintName())) {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LINK_EXISTS",
                            "La relación entre estas incidencias ya existe.");
                }
            }
            throw ex;
        }
        audit.save(new AuditEvent(actor, "DUPLICATE_LINK_CONFIRMED", "DUPLICATE_LINK", link.getId().toString(),
                Map.of("incidentId", first.getId().toString(), "candidateIncidentId", second.getId().toString()),
                link.getLinkedAt()));
        return toDto(link, first.getId());
    }


    private static String normalizeReason(String value) {
        if (value == null) return null;
        String reason = value.trim();
        int length = reason.codePointCount(0, reason.length());
        if (length > 2000 || value.codePoints().anyMatch(c -> c == 0 || (c >= 0xD800 && c <= 0xDFFF))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
                    "El motivo supera los límites admitidos.");
        }
        return reason.isEmpty() ? null : reason;
    }

    private Incident incident(UUID id) {
        if (id == null) throw notFound();
        return incidents.findById(id).orElseThrow(DuplicateService::notFound);
    }

    private static DuplicateLinkDTO toDto(DuplicateLink link, UUID requestedId) {
        UUID other = link.getIncident().getId().equals(requestedId)
                ? link.getCandidateIncident().getId() : link.getIncident().getId();
        return new DuplicateLinkDTO(link.getId(), requestedId, other,
                UserSummaryDTO.from(link.getLinkedBy()), link.getLinkedAt());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "INCIDENT_NOT_FOUND", "Incidencia no encontrada.");
    }
}
