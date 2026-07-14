package emc.mapIt.service;

import emc.mapIt.entity.PasswordResetToken;
import emc.mapIt.entity.User;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.PasswordResetTokenRepository;
import emc.mapIt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Ciclo de vida de los tokens de restablecimiento de contraseña ("olvidé mi contraseña",
 * Fase 1 auth): emisión tras solicitud del usuario y canje para fijar una contraseña nueva.
 * <p>
 * Responsabilidad única, hermana de {@link EmailVerificationService} y con la misma forma
 * (token aleatorio de un solo uso, hash SHA-256 persistido con TTL). A diferencia del reenvío
 * de verificación, {@link #requestReset(String)} sí revela si el email existe (decisión de
 * producto explícita: el registro ya lo revela vía 409 CONFLICT, así que no hay anti-enumeración
 * real que proteger aquí).
 * </p>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final HashService hashService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final int resetTtlMinutes;
    private final int resendCooldownSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            HashService hashService,
            MailService mailService,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            @Value("${mapit.mail.reset-ttl-minutes}") int resetTtlMinutes,
            @Value("${mapit.mail.resend-cooldown-seconds}") int resendCooldownSeconds) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.hashService = hashService;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.resetTtlMinutes = resetTtlMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    /**
     * Emite un token de reset y envía el correo correspondiente, si el email existe y no está
     * en cooldown (mismo cooldown server-side que {@code resend-verification}, en silencio: no
     * informar de él no filtra nada nuevo una vez que ya se ha revelado que el email existe).
     *
     * @throws ApiException NOT_FOUND si no existe ninguna cuenta con ese email
     */
    public void requestReset(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(
                        "NOT_FOUND",
                        "No existe ninguna cuenta con ese correo electrónico.",
                        HttpStatus.NOT_FOUND));

        Optional<PasswordResetToken> lastToken =
                tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId());
        if (lastToken.isPresent() && isWithinCooldown(lastToken.get())) {
            log.info("Solicitud de reset de contraseña ignorada por cooldown userId={}", user.getId());
            return;
        }

        issueAndSend(user);
    }

    /**
     * Canjea un token de reset válido y fija la contraseña nueva, aplicando la misma política
     * de fortaleza que en registro. Invalida además cualquier otro token de reset pendiente del
     * usuario, cerrando huecos de seguridad tras un cambio de contraseña exitoso.
     *
     * @throws ApiException INVALID_TOKEN si el token no existe, ya expiró o ya fue consumido
     *                       (un único motivo genérico, no se distingue el caso concreto)
     * @throws ApiException WEAK_PASSWORD / PASSWORD_TOO_LONG si la contraseña nueva no cumple
     *                       la política (ver {@link PasswordPolicyService})
     */
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(hashService.sha256(rawToken))
                .orElseThrow(this::invalidTokenException);

        if (token.isConsumed() || token.isExpired()) {
            throw invalidTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(this::invalidTokenException);

        passwordPolicyService.validate(newPassword, List.of(user.getName(), user.getNick(), user.getEmail()));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setConsumedAt(Instant.now());
        tokenRepository.save(token);
        tokenRepository.deleteByUserIdAndConsumedAtIsNull(user.getId());

        log.info("Contraseña restablecida userId={}", user.getId());
    }

    private void issueAndSend(User user) {
        tokenRepository.deleteByUserIdAndConsumedAtIsNull(user.getId());

        String rawToken = generateRawToken();
        Instant now = Instant.now();

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(hashService.sha256(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(resetTtlMinutes, ChronoUnit.MINUTES));
        tokenRepository.save(token);

        mailService.sendPasswordResetEmail(user.getEmail(), user.getName(), rawToken);
        log.info("Token de reset de contraseña emitido userId={}", user.getId());
    }

    private boolean isWithinCooldown(PasswordResetToken lastToken) {
        return Instant.now().isBefore(lastToken.getCreatedAt().plusSeconds(resendCooldownSeconds));
    }

    private ApiException invalidTokenException() {
        return new ApiException(
                "INVALID_TOKEN",
                "El enlace de restablecimiento no es válido o ha caducado.",
                HttpStatus.BAD_REQUEST);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
