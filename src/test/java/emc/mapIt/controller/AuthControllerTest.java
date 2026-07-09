package emc.mapIt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import emc.mapIt.dto.AuthLoginRequest;
import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.dto.AuthResponse;
import emc.mapIt.dto.MapItUserResponse;
import emc.mapIt.entity.UserType;
import emc.mapIt.service.AuthService;
import emc.mapIt.service.JwtService;
import emc.mapIt.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
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

    // ── POST /register ───────────────────────────────────────────

    @Test
    void register_conBodyValido_devuelve201ConToken() throws Exception {
        when(authService.register(any())).thenReturn(AUTH_RESPONSE);

        AuthRegisterRequest body = new AuthRegisterRequest("Ana", "ana@test.com", "pass123", UserType.PARTICULAR);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.email").value("ana@test.com"));
    }

    @Test
    void register_conEmailInvalido_devuelve400() throws Exception {
        String bodyInvalido = """
                {"name":"Ana","email":"no-es-un-email","password":"pass123","userType":"PARTICULAR"}
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

    // ── POST /login ──────────────────────────────────────────────

    @Test
    void login_conCredencialesValidas_devuelve200ConToken() throws Exception {
        when(authService.login(any())).thenReturn(AUTH_RESPONSE);

        AuthLoginRequest body = new AuthLoginRequest("ana@test.com", "pass123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_conEmailInvalido_devuelve400() throws Exception {
        String bodyInvalido = """
                {"email":"no-es-email","password":"pass123"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyInvalido))
                .andExpect(status().isBadRequest());
    }

    // ── POST /logout ─────────────────────────────────────────────

    @Test
    void logout_devuelve204SinCuerpo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
