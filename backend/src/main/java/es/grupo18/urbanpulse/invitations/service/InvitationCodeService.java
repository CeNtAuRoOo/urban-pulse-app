package es.grupo18.urbanpulse.invitations.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import es.grupo18.urbanpulse.auth.config.EmailVerificationProperties;
import es.grupo18.urbanpulse.invitations.config.StaffInvitationProperties;
import es.grupo18.urbanpulse.shared.security.JwtProperties;

/**
 * Generación de 256 bits y búsqueda por HMAC con separación de claves y
 * propósito.
 */
@Service
@EnableConfigurationProperties(StaffInvitationProperties.class)
public class InvitationCodeService {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]");
    private static final byte[] PURPOSE = "UrbanPulse:staff-invitation:v1\0".getBytes(StandardCharsets.US_ASCII);
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    /**
     * Impide reutilizar secretos de autenticación o verificación.
     *
     * @param properties   secreto propio y vigencia
     * @param jwt          configuración JWT
     * @param verification configuración de verificación de email
     * @throws IllegalArgumentException si las claves son inválidas o coincidentes
     */
    public InvitationCodeService(StaffInvitationProperties properties, JwtProperties jwt,
            EmailVerificationProperties verification) {

        byte[] bytes = properties.keyBytes();

        if (MessageDigest.isEqual(bytes, jwt.keyBytes())
                || MessageDigest.isEqual(bytes, verification.keyBytes())) {
            throw new IllegalArgumentException("La clave de invitaciones debe ser independiente.");
        }

        key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** @return código aleatorio Base64URL de 43 caracteres sin padding */
    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Comprueba alfabeto, longitud y bits finales canónicos, sin calcular HMAC.
     *
     * @param code código recibido
     * @return true si tiene el formato canónico de 32 bytes
     */
    public boolean validFormat(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }

    /**
     * Calcula el único valor persistible del código.
     *
     * @param code código canónico
     * @return HMAC de 32 bytes
     * @throws IllegalArgumentException si el formato no es válido
     * @throws IllegalStateException    si falta el algoritmo criptográfico
     */
    public byte[] hash(String code) {
        if (!validFormat(code)) {
            throw new IllegalArgumentException("Formato de invitación no válido.");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(PURPOSE);
            return mac.doFinal(code.getBytes(StandardCharsets.US_ASCII));
        }

        catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC no disponible.");
        }
    }
}
