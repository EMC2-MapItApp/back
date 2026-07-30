package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.AuthLoginRequest;
import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.dto.AuthResponse;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.dto.RegisterResponse;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.mapper.AuthRegisterToUserMapper;
import emc.mapIt.mapper.UserWithProfileToMapItUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthRegisterToUserMapper authRegisterToUserMapper;
    @Mock private UserWithProfileToMapItUserMapper userWithProfileToMapItUserMapper;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private PasswordResetService passwordResetService;

    private AuthService authService;

    // Fixtures reutilizables
    private User userGuardado;
    private MapItUser mapItUser;
    private MapItUserResponse userResponse;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userService, passwordEncoder, jwtService,
                authRegisterToUserMapper, userWithProfileToMapItUserMapper,
                passwordPolicyService, emailVerificationService, passwordResetService);

        userGuardado = new User();
        userGuardado.setId("id-1");
        userGuardado.setEmail("ana@test.com");

        mapItUser = new MapItUser("id-1", "Ana", "ana@test.com", "hash", UserType.PARTICULAR);
        mapItUser.setEmailVerified(true);

        userResponse = new MapItUserResponse(
                "id-1", "Ana", "ana", "ana@test.com", UserType.PARTICULAR,
                0, 0, null, null, null, null, null, null, null, null, null, null);
    }

    // ── register ────────────────────────────────────────────────

    @Test
    void register_conDatosValidos_devuelveRegisterResponseSinToken() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "ana", "ana@test.com", "pass1234", UserType.PARTICULAR);

        when(authRegisterToUserMapper.toEntity(request)).thenReturn(userGuardado);
        when(passwordEncoder.encode("pass1234")).thenReturn("bcrypt-hash");
        when(userService.create(userGuardado)).thenReturn(userGuardado);

        RegisterResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("ana@test.com");
        verify(passwordPolicyService).validate("pass1234", List.of("Ana", "ana", "ana@test.com"));
        verify(emailVerificationService).issueAndSend(userGuardado);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void register_conPasswordDebil_lanzaApiExceptionYNoCreaUsuario() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "ana", "ana@test.com", "12345678", UserType.PARTICULAR);

        doThrow(new ApiException("WEAK_PASSWORD", "debil", org.springframework.http.HttpStatus.BAD_REQUEST))
                .when(passwordPolicyService).validate(eq("12345678"), any());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class);

        verify(userService, never()).create(any());
        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void register_conRequestNull_lanzaApiException() {
        assertThatThrownBy(() -> authService.register(null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requerida");
    }

    @Test
    void register_conEmailBlanco_lanzaApiException() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "ana", "  ", "pass1234", UserType.PARTICULAR);
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void register_conPasswordBlanca_lanzaApiException() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "ana", "ana@test.com", "", UserType.PARTICULAR);
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void register_conUserTypeNull_lanzaApiException() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "ana", "ana@test.com", "pass1234", null);
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class);
    }

    // ── login ────────────────────────────────────────────────────

    @Test
    void login_conUsuarioVerificado_devuelveAuthResponse() {
        AuthLoginRequest request = new AuthLoginRequest("ana@test.com", "pass1234");

        when(userService.getByEmailOrThrow("ana@test.com")).thenReturn(mapItUser);
        when(passwordEncoder.matches("pass1234", "hash")).thenReturn(true);
        when(jwtService.generateToken("id-1")).thenReturn("jwt-token");
        when(userService.toResponse(mapItUser)).thenReturn(userResponse);

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().email()).isEqualTo("ana@test.com");
    }

    @Test
    void login_conUsuarioNoVerificado_lanzaApiExceptionForbidden() {
        mapItUser.setEmailVerified(false);
        AuthLoginRequest request = new AuthLoginRequest("ana@test.com", "pass1234");

        when(userService.getByEmailOrThrow("ana@test.com")).thenReturn(mapItUser);
        when(passwordEncoder.matches("pass1234", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("verificar");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_conPasswordIncorrecta_lanzaApiException() {
        AuthLoginRequest request = new AuthLoginRequest("ana@test.com", "wrongpass");

        when(userService.getByEmailOrThrow("ana@test.com")).thenReturn(mapItUser);
        when(passwordEncoder.matches("wrongpass", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalidas");
    }

    @Test
    void login_conRequestNull_lanzaApiException() {
        assertThatThrownBy(() -> authService.login(null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void login_conEmailBlanco_lanzaApiException() {
        AuthLoginRequest request = new AuthLoginRequest("", "pass1234");
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void login_conNickValido_devuelveAuthResponse() {
        AuthLoginRequest request = new AuthLoginRequest("@ana", "pass1234");

        when(userService.getByNickOrThrow("ana")).thenReturn(mapItUser);
        when(passwordEncoder.matches("pass1234", "hash")).thenReturn(true);
        when(jwtService.generateToken("id-1")).thenReturn("jwt-token");
        when(userService.toResponse(mapItUser)).thenReturn(userResponse);

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        verify(userService, never()).getByEmailOrThrow(any());
    }

    @Test
    void login_conNickInexistente_lanzaApiException() {
        AuthLoginRequest request = new AuthLoginRequest("@noexiste", "pass1234");

        when(userService.getByNickOrThrow("noexiste")).thenThrow(
                new ApiException("INVALID_CREDENTIALS", "Credenciales invalidas", org.springframework.http.HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalidas");
    }

    @Test
    void login_conIdentifierSinFormatoValido_lanzaApiExceptionSinConsultarUsuario() {
        AuthLoginRequest request = new AuthLoginRequest("no-es-nick-ni-email", "pass1234");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalidas");

        verify(userService, never()).getByEmailOrThrow(any());
        verify(userService, never()).getByNickOrThrow(any());
    }

    // ── forgotPassword / resetPassword ─────────────────────────────

    @Test
    void forgotPassword_delegaEnPasswordResetService() {
        authService.forgotPassword("ana@test.com");

        verify(passwordResetService).requestReset("ana@test.com");
    }

    @Test
    void resetPassword_delegaEnPasswordResetService() {
        authService.resetPassword("token-en-claro", "nuevaPass1234");

        verify(passwordResetService).resetPassword("token-en-claro", "nuevaPass1234");
    }
}
