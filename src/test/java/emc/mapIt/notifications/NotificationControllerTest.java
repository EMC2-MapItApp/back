package emc.mapIt.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de controlador con seguridad excluida (mismo criterio que {@code AuthControllerTest}):
 * {@link emc.mapIt.service.AuthService} se mockea, así que no valida tokens reales — el objetivo
 * aquí es la forma de las rutas y las respuestas, no las reglas de {@code SecurityConfig}.
 */
@WebMvcTest(
        controllers = NotificationController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@TestPropertySource(properties = "mapit.push.vapid.public-key=test-public-key")
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean NotificationService notificationService;
    @MockitoBean NotificationMapper notificationMapper;
    @MockitoBean emc.mapIt.service.AuthService authService;
    // JwtAuthFilter (Filter bean) se escanea igualmente pese a excluir SecurityAutoConfiguration
    // — mismo motivo por el que AuthControllerTest también lo mockea.
    @MockitoBean emc.mapIt.service.JwtService jwtService;

    private static final String USER_ID = "user-1";

    @Test
    void list_devuelveNotificacionesMapeadas() throws Exception {
        when(authService.requireUserId(any())).thenReturn(USER_ID);
        Notification entity = new Notification();
        entity.setId("n-1");
        when(notificationService.listForUser(USER_ID)).thenReturn(List.of(entity));
        NotificationResponse response = new NotificationResponse(
                "n-1", NotificationType.GROUP_INVITATION, "Título", "Cuerpo", "/groups", false, Instant.now());
        when(notificationMapper.toResponse(entity)).thenReturn(response);

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("n-1"))
                .andExpect(jsonPath("$[0].title").value("Título"));
    }

    @Test
    void unreadCount_devuelveContador() throws Exception {
        when(authService.requireUserId(any())).thenReturn(USER_ID);
        when(notificationService.unreadCount(USER_ID)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void markRead_marcaLaNotificacionComoLeida() throws Exception {
        when(authService.requireUserId(any())).thenReturn(USER_ID);

        mockMvc.perform(patch("/api/v1/notifications/n-1/read"))
                .andExpect(status().isNoContent());

        verify(notificationService).markRead(USER_ID, "n-1");
    }

    @Test
    void markAllRead_marcaTodasComoLeidas() throws Exception {
        when(authService.requireUserId(any())).thenReturn(USER_ID);

        mockMvc.perform(post("/api/v1/notifications/read-all"))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllRead(USER_ID);
    }

    @Test
    void pushPublicKey_devuelveLaClaveConfigurada() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/push/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("test-public-key"));
    }

    @Test
    void registerPushSubscription_conBodyValido_registraLaSuscripcion() throws Exception {
        when(authService.requireUserId(any())).thenReturn(USER_ID);
        String body = """
                {"endpoint":"https://push.example.com/abc","keys":{"p256dh":"key-p256dh","auth":"key-auth"}}
                """;

        mockMvc.perform(post("/api/v1/notifications/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(notificationService).registerSubscription(eq(USER_ID), any(PushSubscriptionRequest.class));
    }

    @Test
    void registerPushSubscription_sinEndpoint_devuelve400() throws Exception {
        String body = """
                {"keys":{"p256dh":"key-p256dh","auth":"key-auth"}}
                """;

        mockMvc.perform(post("/api/v1/notifications/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unregisterPushSubscription_borraPorEndpoint() throws Exception {
        when(authService.requireUserId(any())).thenReturn(USER_ID);
        String body = """
                {"endpoint":"https://push.example.com/abc"}
                """;

        mockMvc.perform(delete("/api/v1/notifications/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(notificationService).unregisterSubscription("https://push.example.com/abc");
    }

    @Test
    void preferences_devuelveElEstadoDeCadaTipo() throws Exception {
        when(authService.requireUserId(any())).thenReturn(USER_ID);
        when(notificationService.getPreferences(USER_ID)).thenReturn(List.of(
                new NotificationPreferenceResponse(NotificationType.GROUP_INVITATION, true),
                new NotificationPreferenceResponse(NotificationType.GROUP_BROADCAST, false)));

        mockMvc.perform(get("/api/v1/notifications/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("GROUP_INVITATION"))
                .andExpect(jsonPath("$[0].emailEnabled").value(true))
                .andExpect(jsonPath("$[1].type").value("GROUP_BROADCAST"))
                .andExpect(jsonPath("$[1].emailEnabled").value(false));
    }

    @Test
    void updatePreference_desactivaElEmailDeUnTipo() throws Exception {
        when(authService.requireUserId(any())).thenReturn(USER_ID);
        String body = """
                {"emailEnabled":false}
                """;

        mockMvc.perform(patch("/api/v1/notifications/preferences/GROUP_BROADCAST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(notificationService).updateEmailPreference(USER_ID, NotificationType.GROUP_BROADCAST, false);
    }

    @Test
    void updatePreference_sinEmailEnabled_devuelve400() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/preferences/GROUP_BROADCAST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
