package es.grupo18.urbanpulse.invitations.model;

/**
 * Estado derivado de una invitación; nunca se persiste como columna redundante.
 */
public enum InvitationStatus {
    ACTIVE,
    REDEEMED,
    REVOKED,
    EXPIRED
}
