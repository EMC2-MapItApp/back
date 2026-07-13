package emc.mapIt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import emc.mapIt.dto.AuthLoginRequest;
import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.dto.AuthResponse;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.dto.RegisterResponse;
import emc.mapIt.dto.ResendVerificationRequest;
import emc.mapIt.dto.VerifyEmailRequest;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.service.AuthService;
import emc.mapIt.service.JwtService;
import emc.mapIt.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        // Excluimos Security para no necesitar configurar JwtAuthFilter en tests de controlador
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;

    private static final MapItUserResponse USER_RESPONSE = new MapItUserResponse(
            "id-1", "Ana", "ana@test.com", UserType.PARTICULAR,
            0, 0, null, null, null, null, null, null, null, null, null, null);

    private static final AuthResponse AUTH_RESPONSE = new AuthResponse("jwt-token", USER_RESPONSE);
    private static final RegisterResponse REGISTER_RESPONSE = new RegisterResponse("ana@test.com");

    // ── POST /register ───────────────────────────────────────────

    @Test
    void register_conBodyValido_devuelve201SinToken() throws Exception {
        when(authService.register(any())).thenReturn(REGISTER_RESPONSE);

        AuthRegisterRequest body = new AuthRegisterRequest("Ana", "ana@test.com", "pass1234", UserType.PARTICULAR);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@test.com"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void register_conEmailInvalido_devuelve400() throws Exception {
        String bodyInvalido = """
                {"name":"Ana","email":"no-es-un-email","password":"pass1234","userType":"PARTICULAR"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_sinBody_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_conPasswordCortaMenosDe8_devuelve400() throws Exception {
        String bodyInvalido = """
                {"name":"Ana","email":"ana@test.com","password":"corta1","userType":"PARTICULAR"}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_conPasswordDebil_devuelve400() throws Exception {
        when(authService.register(any())).thenThrow(
                new ApiException("WEAK_PASSWORD", "La contraseña es demasiado débil.", HttpStatus.BAD_REQUEST));

        AuthRegisterRequest body = new AuthRegisterRequest("Ana", "ana@test.com", "password", UserType.PARTICULAR);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"));
    }

    // ── POST /login ──────────────────────────────────────────────

    @Test
    void login_conCredencialesValidas_devuelve200ConToken() throws Exception {
        when(authService.login(any())).thenReturn(AUTH_RESPONSE);

        AuthLoginRequest body = new AuthLoginRequest("ana@test.com", "pass1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_conEmailInvalido_devuelve400() throws Exception {
        String bodyInvalido = """
                {"email":"no-es-email","password":"pass1234"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_conUsuarioNoVerificado_devuelve403() throws Exception {
        when(authService.login(any())).thenThrow(new ApiException(
                "EMAIL_NOT_VERIFIED", "Debes verificar tu correo electronico antes de iniciar sesion.",
                HttpStatus.FORBIDDEN));

        AuthLoginRequest body = new AuthLoginRequest("ana@test.com", "pass1234");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
    }

    // ── POST /verify-email ──────────────────────────────────────

    @Test
    void verifyEmail_conTokenValido_devuelve200() throws Exception {
        VerifyEmailRequest body = new VerifyEmailRequest("token-valido");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void verifyEmail_conTokenInvalido_devuelve400() throws Exception {
        doThrow(new ApiException("INVALID_TOKEN", "invalido", HttpStatus.BAD_REQUEST))
                .when(authService).verifyEmail(anyString());

        VerifyEmailRequest body = new VerifyEmailRequest("token-invalido");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    // ── POST /resend-verification ───────────────────────────────

    @Test
    void resendVerification_siempreDevuelve200ConMensajeGenerico() throws Exception {
        ResendVerificationRequest body = new ResendVerificationRequest("no-registrado@test.com");

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    // ── POST /logout ─────────────────────────────────────────────

    @Test
    void logout_devuelve204SinCuerpo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
