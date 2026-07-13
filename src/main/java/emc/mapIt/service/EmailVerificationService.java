package emc.mapIt.service;

import emc.mapIt.entity.EmailVerificationToken;
import emc.mapIt.entity.User;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.EmailVerificationTokenRepository;
import emc.mapIt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Ciclo de vida de los tokens de verificacion de email (Fase 1 auth): emision, consumo
 * y reenvio con cooldown server-side.
 * <p>
 * El cooldown de {@link #resend(String)} es el control real anti-abuso; el debounce de
 * 60s del frontend es solo UX y es trivialmente evitable desde fuera del navegador.
 * </p>
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final HashService hashService;
    private final MailService mailService;
    private final int verificationTtlMinutes;
    private final int resendCooldownSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            HashService hashService,
            MailService mailService,
            @Value("${mapit.mail.verification-ttl-minutes}") int verificationTtlMinutes,
            @Value("${mapit.mail.resend-cooldown-seconds}") int resendCooldownSeconds) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.hashService = hashService;
        this.mailService = mailService;
        this.verificationTtlMinutes = verificationTtlMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    /**
     * Emite un token nuevo (invalidando cualquier token no consumido previo del usuario)
     * y envia el correo de verificacion. Se invoca tras el registro y en cada reenvio.
     */
    public void issueAndSend(User user) {
        tokenRepository.deleteByUserIdAndConsumedAtIsNull(user.getId());

        String rawToken = generateRawToken();
        Instant now = Instant.now();

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(user.getId());
        token.setTokenHash(hashService.sha256(rawToken));
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(verificationTtlMinutes, ChronoUnit.MINUTES));
        tokenRepository.save(token);

        mailService.sendVerificationEmail(user.getEmail(), user.getName(), rawToken);
        log.info("Token de verificacion emitido userId={}", user.getId());
    }

    /**
     * Consume un token y marca el usuario como verificado.
     *
     * @throws ApiException INVALID_TOKEN si el token no existe, ya expiro o ya fue consumido
     *                       (un unico motivo genérico, no se distingue el caso concreto)
     */
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(hashService.sha256(rawToken))
                .orElseThrow(this::invalidTokenException);

        if (token.isConsumed() || token.isExpired()) {
            throw invalidTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(this::invalidTokenException);

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        token.setConsumedAt(Instant.now());
        tokenRepository.save(token);

        log.info("Email verificado userId={}", user.getId());
    }

    /**
     * Reenvia el correo de verificacion si el email existe, no esta ya verificado y no
     * esta en cooldown. En cualquier otro caso no hace nada, pero el llamador (controller)
     * responde siempre igual: no se debe poder distinguir desde fuera si el email existe.
     */
    public void resend(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(normalizeEmail(email));
        if (maybeUser.isEmpty()) {
            return;
        }

        User user = maybeUser.get();
        if (user.isEmailVerified()) {
            return;
        }

        Optional<EmailVerificationToken> lastToken =
                tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId());
        if (lastToken.isPresent() && isWithinCooldown(lastToken.get())) {
            log.info("Reenvio de verificacion ignorado por cooldown userId={}", user.getId());
            return;
        }

        issueAndSend(user);
    }

    private boolean isWithinCooldown(EmailVerificationToken lastToken) {
        return Instant.now().isBefore(lastToken.getCreatedAt().plusSeconds(resendCooldownSeconds));
    }

    private ApiException invalidTokenException() {
        return new ApiException(
                "INVALID_TOKEN",
                "El enlace de verificacion no es valido o ha caducado.",
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
