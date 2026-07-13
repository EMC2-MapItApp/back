package emc.mapIt.service;

import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;
import emc.mapIt.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Decide si una contraseña candidata es aceptable (Fase 1 auth).
 * <p>
 * Responsabilidad unica: politica de contraseñas. No conoce nada de registro/login.
 * </p>
 */
@Service
public class PasswordPolicyService {

    private static final Logger log = LoggerFactory.getLogger(PasswordPolicyService.class);

    // BCrypt solo procesa los primeros 72 bytes de entrada; por encima de eso trunca en
    // silencio. Se rechaza explicitamente en vez de dejar que ese limite actue sin avisar.
    private static final int MAX_BCRYPT_BYTES = 72;

    // Escala de zxcvbn: 0 (muy debil) a 4 (muy fuerte). TareasLogin.md exige minimo 3.
    private static final int MIN_ACCEPTABLE_SCORE = 3;

    private final Zxcvbn zxcvbn = new Zxcvbn();

    /**
     * Valida la contraseña o lanza {@link ApiException} si no es aceptable.
     *
     * @param password    contraseña candidata en claro
     * @param userInputs  valores propios del usuario (nombre, email) para que zxcvbn
     *                    penalice contraseñas derivadas de ellos
     */
    public void validate(String password, List<String> userInputs) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new ApiException(
                    "PASSWORD_TOO_LONG",
                    "La contraseña no puede superar los 72 bytes efectivos.",
                    HttpStatus.BAD_REQUEST);
        }

        Strength strength = zxcvbn.measure(password, userInputs);
        if (strength.getScore() < MIN_ACCEPTABLE_SCORE) {
            log.info("Password rechazada por baja entropia score={}", strength.getScore());
            throw new ApiException(
                    "WEAK_PASSWORD",
                    "La contraseña es demasiado débil. Usa una combinación más difícil de adivinar.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
