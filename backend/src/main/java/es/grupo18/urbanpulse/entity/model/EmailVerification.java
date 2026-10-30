package es.grupo18.urbanpulse.entity.model;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;

/**
 * Representa una solicitud de verificación del correo electrónico
 * asociada a una cuenta de usuario.
 * Almacena el HMAC del código y sus fechas de emisión y caducidad,
 * manteniendo como máximo una solicitud por usuario.
 */
@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @Getter(AccessLevel.NONE)
    private User user;

    @Column(nullable = false, columnDefinition = "bytea")
    @Getter(AccessLevel.NONE)
    private byte[] codeHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * Crea una solicitud de verificación asociada a un usuario.
     * Su identificador se obtiene del usuario mediante la relación
     * de clave primaria compartida definida con MapsId.
     *
     * @param user usuario propietario de la solicitud de verificación
     */
    public EmailVerification(User user) {
        this.user = user;
    }

    /**
     * Sustituye los datos de la solicitud de verificación por un nuevo
     * HMAC y nuevas fechas de emisión y caducidad.
     * Conserva una copia independiente del hash proporcionado.
     *
     * @param hash    HMAC del nuevo código de verificación
     * @param now     instante de emisión del nuevo código
     * @param expires instante de caducidad del nuevo código
     */
    public void replace(byte[] hash, Instant now, Instant expires) {
        codeHash = hash.clone();
        createdAt = now;
        expiresAt = expires;
    }

    /**
     * Obtiene una copia del HMAC almacenado para evitar modificaciones
     * externas del array de bytes de la entidad.
     *
     * @return copia del HMAC del código de verificación
     */
    public byte[] getCodeHash() {
        return codeHash.clone();
    }
}
