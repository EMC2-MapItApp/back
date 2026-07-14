package emc.mapIt.service;

import emc.mapIt.entity.PasswordResetToken;
import emc.mapIt.entity.User;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.PasswordResetTokenRepository;
import emc.mapIt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final int TTL_MINUTES = 15;
    private static final int COOLDOWN_SECONDS = 60;

    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private HashService hashService;
    @Mock private MailService mailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordPolicyService passwordPolicyService;

    private PasswordResetService passwordResetService;

    private User usuario;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                tokenRepository, userRepository, hashService, mailService,
                passwordEncoder, passwordPolicyService, TTL_MINUTES, COOLDOWN_SECONDS);

        usuario = new User();
        usuario.setId("id-1");
        usuario.setName("Ana");
        usuario.setNick("ana");
        usuario.setEmail("ana@test.com");
        usuario.setPasswordHash("hash-antiguo");
    }

    // ── requestReset ─────────────────────────────────────────────

    @Test
    void requestReset_conEmailRegistrado_creaTokenYEnviaCorreo() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario));
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");

        passwordResetService.requestReset("ana@test.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());

        PasswordResetToken guardado = captor.getValue();
        assertThat(guardado.getUserId()).isEqualTo("id-1");
        assertThat(guardado.getTokenHash()).isEqualTo("hash-del-token");
        assertThat(guardado.getConsumedAt()).isNull();

        verify(mailService).sendPasswordResetEmail(eq("ana@test.com"), eq("Ana"), anyString());
    }

    @Test
    void requestReset_conEmailNoRegistrado_lanzaApiExceptionNotFound() {
        when(userRepository.findByEmail("noexiste@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.requestReset("noexiste@test.com"))
                .isInstanceOf(ApiException.class);

        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void requestReset_dentroDeCooldown_noReenviaCorreo() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario));
        PasswordResetToken tokenReciente = tokenValido();
        tokenReciente.setCreatedAt(Instant.now().minusSeconds(10));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("id-1"))
                .thenReturn(Optional.of(tokenReciente));

        passwordResetService.requestReset("ana@test.com");

        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    void requestReset_fueraDeCooldown_reenviaCorreo() {
        when(userRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(usuario));
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        PasswordResetToken tokenAntiguo = tokenValido();
        tokenAntiguo.setCreatedAt(Instant.now().minusSeconds(COOLDOWN_SECONDS + 30));
        when(tokenRepository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc("id-1"))
                .thenReturn(Optional.of(tokenAntiguo));

        passwordResetService.requestReset("ana@test.com");

        verify(mailService).sendPasswordResetEmail(eq("ana@test.com"), eq("Ana"), anyString());
    }

    // ── resetPassword ────────────────────────────────────────────

    @Test
    void resetPassword_conTokenValido_actualizaPasswordYConsumeToken() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        PasswordResetToken token = tokenValido();
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.of(token));
        when(userRepository.findById("id-1")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("nuevaPass1234")).thenReturn("hash-nuevo");

        passwordResetService.resetPassword("token-en-claro", "nuevaPass1234");

        verify(passwordPolicyService).validate("nuevaPass1234", List.of("Ana", "ana", "ana@test.com"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hash-nuevo");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getConsumedAt()).isNotNull();

        verify(tokenRepository).deleteByUserIdAndConsumedAtIsNull("id-1");
    }

    @Test
    void resetPassword_conTokenExpirado_lanzaApiException() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        PasswordResetToken token = tokenValido();
        token.setExpiresAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword("token-en-claro", "nuevaPass1234"))
                .isInstanceOf(ApiException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_conTokenYaConsumido_lanzaApiException() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        PasswordResetToken token = tokenValido();
        token.setConsumedAt(Instant.now().minusSeconds(60));
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword("token-en-claro", "nuevaPass1234"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void resetPassword_conTokenInexistente_lanzaApiException() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword("token-en-claro", "nuevaPass1234"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void resetPassword_conPasswordDebil_lanzaApiExceptionYNoActualizaPassword() {
        when(hashService.sha256(anyString())).thenReturn("hash-del-token");
        PasswordResetToken token = tokenValido();
        when(tokenRepository.findByTokenHash("hash-del-token")).thenReturn(Optional.of(token));
        when(userRepository.findById("id-1")).thenReturn(Optional.of(usuario));
        doThrow(new ApiException("WEAK_PASSWORD", "debil", org.springframework.http.HttpStatus.BAD_REQUEST))
                .when(passwordPolicyService).validate(eq("12345678"), any());

        assertThatThrownBy(() -> passwordResetService.resetPassword("token-en-claro", "12345678"))
                .isInstanceOf(ApiException.class);

        verify(userRepository, never()).save(any());
    }

    private PasswordResetToken tokenValido() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId("id-1");
        token.setTokenHash("hash-del-token");
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusSeconds(600));
        return token;
    }
}
