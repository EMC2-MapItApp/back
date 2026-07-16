package emc.mapIt.service;

import emc.mapIt.entity.EmailVerificationToken;
import emc.mapIt.entity.User;
import emc.mapIt.exception.ApiException;
import emc.mapIt.notifications.NotificationSender;
import emc.mapIt.repository.EmailVerificationTokenRepository;
import emc.mapIt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final int TTL_MINUTES = 20;
    private static final int COOLDOWN_SECONDS = 60;

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private HashService hashService;
    @Mock private NotificationSender notificationSender;

    private EmailVerificationService emailVerificationService;

    private User usuario;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(
                tokenRepository, userRepository, hashService, notificationSender,
                TTL_MINUTES, COOLDOWN_SECONDS);

        usuario = new User();
        usuario.setId("id-1");
        usuario.setName("Ana");
        usuario.setEmail("ana@test.com");
        usuario.setEmailVerified(false);
    }

    // ── issueAndSend ────────────────────────────────────────────

    @Test
    void issueAndSend_creaTokenYEnviaCorreo() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");

        emailVerificationService.issueAndSend(usuario);

        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(captor.capture());

        EmailVerificationToken guardado = captor.getValue();
        assertThat(guardado.getUserId()).isEqualTo("id-1");
        assertThat(guardado.getTokenHash()).isEqualTo("hash-del-token");
        assertThat(guardado.getConsumedAt()).isNull();

        verify(notificationSender).sendVerificationEmail(eq("ana@test.com"), eq("Ana"), anyString());
    }

    @Test
    void issueAndSend_invalidaTokenAnteriorAntesDeCrearUnoNuevo() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");

        emailVerificationService.issueAndSend(usuario);

        verify(tokenRepository).deleteByUserIdAndConsumedAtIsNull("id-1");
    }

    // ── verify ───────────────────────────────────────────────────

    @Test
    void verify_conTokenValido_marcaUsuarioVerificado() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        EmailVerificationToken token = tokenValido();
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.of(token));
        when(userRepository.findById("id-1")).thenReturn(Optional.of(usuario));

        emailVerificationService.verify("token-en-claro");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();
        assertThat(userCaptor.getValue().getEmailVerifiedAt()).isNotNull();

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getConsumedAt()).isNotNull();
    }

    @Test
    void verify_conTokenExpirado_lanzaApiException() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        EmailVerificationToken token = tokenValido();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.verify("token-en-claro"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void verify_conTokenYaConsumido_lanzaApiException() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        EmailVerificationToken token = tokenValido();
        token.setConsumedAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.verify("token-en-claro"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void verify_conTokenInexistente_lanzaApiException() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verify("token-en-claro"))
                .isInstanceOf(ApiException.class);
    }

    // ── resend ───────────────────────────────────────────────────

    @Test
    void resend_conEmailNoRegistrado_noLanzaYNoEnviaCorreo() {
        when(userRepository.findByEmail("noexiste@test.com")).thenReturn(Optional.empty());

        emailVerificationService.resend("noexiste@test.com");

        verify(notificationSender, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resend_conEmailYaVerificado_noEnviaCorreo() {
        usuario.setEmailVerified(true);
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario));

        emailVerificationService.resend("ana@test.com");

        verify(notificationSender, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resend_dentroDeCooldown_noReenviaCorreo() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario));
        EmailVerificationToken tokenReciente = tokenValido();
        tokenReciente.setCreatedAt(Instant.now().minusSeconds(10));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("id-1"))
                .thenReturn(Optional.of(tokenReciente));

        emailVerificationService.resend("ana@test.com");

        verify(notificationSender, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    void resend_fueraDeCooldown_reenviaCorreo() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario));
        EmailVerificationToken tokenAntiguo = tokenValido();
        tokenAntiguo.setCreatedAt(Instant.now().minusSeconds(COOLDOWN_SECONDS + 30));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("id-1"))
                .thenReturn(Optional.of(tokenAntiguo));

        emailVerificationService.resend("ana@test.com");

        verify(notificationSender).sendVerificationEmail(eq("ana@test.com"), eq("Ana"), anyString());
    }

    private EmailVerificationToken tokenValido() {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId("id-1");
        token.setTokenHash("hash-del-token");
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        return token;
    }
}
