package emc.mapIt.service;

import emc.mapIt.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
/**
 * Servicio de emision y validacion de token firmado para auth stateless.
 */
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final String secret;
    private final long expirationSeconds;

    /**
     * Construye el servicio con secretos configurables desde propiedades.
     */
    public JwtService(@Value("${mapit.jwt.secret}") String secret,
                      @Value("${mapit.jwt.expiration-seconds}") long expirationSeconds) {
        this.secret = secret;
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * Genera token firmado con subject UUID y expiracion.
     */
    public String generateToken(String userId) {
        long now = Instant.now().getEpochSecond();
        String payloadRaw = userId + ":" + now + ":" + (now + expirationSeconds);
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadRaw.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payload);
        log.debug("Token generado para userId={}", userId);
        return payload + "." + signature;
    }

    /**
     * Extrae userId del header Authorization validando firma y expiracion.
     */
    public String extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ApiException("UNAUTHORIZED", "Token ausente o invalido", HttpStatus.UNAUTHORIZED);
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new ApiException("UNAUTHORIZED", "Token invalido", HttpStatus.UNAUTHORIZED);
        }
        if (!sign(parts[0]).equals(parts[1])) {
            throw new ApiException("UNAUTHORIZED", "Firma de token invalida", HttpStatus.UNAUTHORIZED);
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String[] values = payload.split(":");
            if (values.length != 3) {
                throw new ApiException("UNAUTHORIZED", "Token invalido", HttpStatus.UNAUTHORIZED);
            }
            long exp = Long.parseLong(values[2]);
            if (Instant.now().getEpochSecond() >= exp) {
                throw new ApiException("UNAUTHORIZED", "Token expirado", HttpStatus.UNAUTHORIZED);
            }
            log.debug("Token valido para userId={}", values[0]);
            return values[0];
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Token invalido: {}", ex.getMessage());
            throw new ApiException("UNAUTHORIZED", "Token invalido", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Firma un contenido con HMAC SHA-256 y salida base64url.
     */
    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception ex) {
            log.error("Fallo en firma de token", ex);
            throw new ApiException("INTERNAL_ERROR", "No se pudo firmar token", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

