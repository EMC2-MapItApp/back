package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.AuthLoginRequest;
import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.dto.AuthResponse;
import emc.mapIt.dto.MapItUserResponse;
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

    private AuthService authService;

    // Fixtures reutilizables
    private User userGuardado;
    private MapItUser mapItUser;
    private MapItUserResponse userResponse;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userService, passwordEncoder, jwtService,
                authRegisterToUserMapper, userWithProfileToMapItUserMapper);

        userGuardado = new User();
        userGuardado.setId("id-1");
        userGuardado.setEmail("ana@test.com");

        mapItUser = new MapItUser("id-1", "Ana", "ana@test.com", "hash", UserType.PARTICULAR);

        userResponse = new MapItUserResponse(
                "id-1", "Ana", "ana@test.com", UserType.PARTICULAR,
                0, 0, null, null, null, null, null, null, null, null, null, null);
    }

    // ── register ────────────────────────────────────────────────

    @Test
    void register_conDatosValidos_devuelveAuthResponse() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "ana@test.com", "pass123", UserType.PARTICULAR);

        when(authRegisterToUserMapper.toEntity(request)).thenReturn(userGuardado);
        when(passwordEncoder.encode("pass123")).thenReturn("bcrypt-hash");
        when(userService.create(userGuardado)).thenReturn(userGuardado);
        when(jwtService.generateToken("id-1")).thenReturn("jwt-token");
        when(userWithProfileToMapItUserMapper.toDomain(userGuardado, null)).thenReturn(mapItUser);
        when(userService.toResponse(mapItUser)).thenReturn(userResponse);

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo("id-1");
        assertThat(response.user().email()).isEqualTo("ana@test.com");
    }

    @Test
    void register_conRequestNull_lanzaApiException() {
        assertThatThrownBy(() -> authService.register(null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requerida");
    }

    @Test
    void register_conEmailBlanco_lanzaApiException() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "  ", "pass123", UserType.PARTICULAR);
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void register_conPasswordBlanca_lanzaApiException() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "ana@test.com", "", UserType.PARTICULAR);
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void register_conUserTypeNull_lanzaApiException() {
        AuthRegisterRequest request = new AuthRegisterRequest("Ana", "ana@test.com", "pass123", null);
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class);
    }

    // ── login ────────────────────────────────────────────────────

    @Test
    void login_conCredencialesCorrectas_devuelveAuthResponse() {
        AuthLoginRequest request = new AuthLoginRequest("ana@test.com", "pass123");

        when(userService.getByEmailOrThrow("ana@test.com")).thenReturn(mapItUser);
        when(passwordEncoder.matches("pass123", "hash")).thenReturn(true);
        when(jwtService.generateToken("id-1")).thenReturn("jwt-token");
        when(userService.toResponse(mapItUser)).thenReturn(userResponse);

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().email()).isEqualTo("ana@test.com");
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
        AuthLoginRequest request = new AuthLoginRequest("", "pass123");
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class);
    }
}
