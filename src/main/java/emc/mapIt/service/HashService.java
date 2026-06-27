package emc.mapIt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
/**
 * Utilidad para generar hash SHA-256 de valores sensibles.
 */
public class HashService {

    private static final Logger log = LoggerFactory.getLogger(HashService.class);

    /**
     * Calcula hash SHA-256 en formato hexadecimal.
     */
    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            log.error("Fallo calculando SHA-256", ex);
            throw new IllegalStateException("No se pudo calcular hash", ex);
        }
    }
}
