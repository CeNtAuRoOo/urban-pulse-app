package es.grupo18.urbanpulse.maps.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import es.grupo18.urbanpulse.identity.model.RoleCode;
import es.grupo18.urbanpulse.identity.repository.UserRepository;
import es.grupo18.urbanpulse.shared.error.ApiException;

/** Rechecks current database security state even when invoked outside the controller. */
@Component
@RequiredArgsConstructor
public class MapVisibilityPolicy {
    private final UserRepository users;

    public Access authorize(UUID actor, long tokenVersion) {
        var rows = users.findSecurityState(actor);
        if (rows.isEmpty() || !rows.getFirst().getActive() || rows.getFirst().getTokenVersion() != tokenVersion
                || rows.stream().noneMatch(r -> r.getRoleCode() == RoleCode.CITIZEN)) {
            throw ApiException.unauthorized();
        }
        if (!rows.getFirst().getEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Debes verificar tu correo.");
        }
        return new Access(actor, rows.stream().anyMatch(r -> r.getRoleCode() == RoleCode.OPERATOR
                || r.getRoleCode() == RoleCode.ADMIN));
    }

    public record Access(UUID actor, boolean global) { }
}
