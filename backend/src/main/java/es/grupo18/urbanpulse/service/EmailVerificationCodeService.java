package es.grupo18.urbanpulse.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import es.grupo18.urbanpulse.config.*;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado de generar y proteger los códigos de verificación
 * del correo electrónico.
 * Utiliza SecureRandom para generar códigos numéricos y HMAC-SHA256
 * para calcular y comprobar sus hashes, asociándolos al usuario
 * y al propósito de verificación.
 */
@Service
public class EmailVerificationCodeService {

    private static final byte[] PURPOSE = "UrbanPulse:email-verification:v1\0".getBytes(StandardCharsets.US_ASCII);
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    /**
     * Inicializa el servicio con la clave secreta de verificación.
     * Comprueba que la clave sea válida e independiente de la utilizada
     * para firmar los tokens JWT.
     *
     * @param properties configuración de seguridad de la verificación
     * @param jwt        configuración de seguridad de los tokens JWT
     * @throws IllegalArgumentException si la clave de verificación no es válida
     *                                  o coincide con la clave JWT
     */
    public EmailVerificationCodeService(EmailVerificationProperties properties, JwtProperties jwt) {
        byte[] bytes = properties.keyBytes();

        if (MessageDigest.isEqual(bytes, jwt.keyBytes())) {
            throw new IllegalArgumentException("Las claves de JWT y verificación deben ser independientes.");
        }

        key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Genera un código de verificación aleatorio de exactamente
     * ocho dígitos decimales ASCII mediante SecureRandom.
     * Conserva los ceros iniciales del código generado.
     *
     * @return código numérico de verificación de ocho caracteres
     */
    public String generate() {
        return String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
    }

    /**
     * Calcula el HMAC-SHA256 de un código de verificación, asociándolo
     * al identificador del usuario y al propósito de verificación.
     * Valida previamente que el código tenga exactamente ocho dígitos ASCII.
     *
     * @param userId identificador del usuario propietario del código
     * @param code   código numérico de verificación en texto plano
     * @return HMAC-SHA256 del código, representado mediante 32 bytes
     * @throws IllegalArgumentException si el código no tiene el formato requerido
     * @throws IllegalStateException    si el algoritmo HMAC no está disponible
     */
    public byte[] hash(UUID userId, String code) {
        if (code == null || !code.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("Formato de código no válido.");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(PURPOSE);

            mac.update(ByteBuffer.allocate(16).putLong(userId.getMostSignificantBits())
                    .putLong(userId.getLeastSignificantBits()).array());

            return mac.doFinal(code.getBytes(StandardCharsets.US_ASCII));
        }

        catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC no disponible.");
        }
    }

    /**
     * Comprueba si un código de verificación coincide con el HMAC esperado.
     * Calcula el HMAC asociado al usuario y utiliza una comparación
     * de bytes resistente a diferencias temporales.
     *
     * @param userId   identificador del usuario propietario del código
     * @param code     código de verificación que se desea comprobar
     * @param expected HMAC almacenado con el que se realizará la comparación
     * @return true si el código coincide con el HMAC esperado; false en caso
     *         contrario
     * @throws IllegalArgumentException si el código no tiene el formato requerido
     */
    public boolean matches(UUID userId, String code, byte[] expected) {
        return MessageDigest.isEqual(hash(userId, code), expected);
    }
}
