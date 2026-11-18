package es.grupo18.urbanpulse.duplicates.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.grupo18.urbanpulse.duplicates.dto.DuplicateLinkDTO;
import es.grupo18.urbanpulse.duplicates.dto.DuplicateLinkRequest;
import es.grupo18.urbanpulse.duplicates.dto.DuplicateSuggestionDTO;
import es.grupo18.urbanpulse.duplicates.service.DuplicateService;
import es.grupo18.urbanpulse.shared.security.DatabaseJwtAuthenticationConverter;

@RestController
@RequestMapping("/api/v1/incidents/{incidentId}")
@PreAuthorize("hasAuthority('EMAIL_VERIFIED') and hasAnyRole('OPERATOR','ADMIN')")
@RequiredArgsConstructor
public class DuplicateController {

    private final DuplicateService service;

    @GetMapping("/duplicate-candidates")
    public ResponseEntity<List<DuplicateSuggestionDTO>> candidates(@PathVariable UUID incidentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.candidates(incidentId));
    }

    @PostMapping("/duplicate-links")
    public ResponseEntity<DuplicateLinkDTO> link(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID incidentId,
            @Valid @RequestBody DuplicateLinkRequest request) {
        DuplicateLinkDTO result = service.link(incidentId, DatabaseJwtAuthenticationConverter.subject(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(result);
    }
}
