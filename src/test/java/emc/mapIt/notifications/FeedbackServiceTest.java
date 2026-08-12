package emc.mapIt.notifications;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.UserRepository;
import emc.mapIt.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationSender notificationSender;

    private FeedbackService feedbackService;

    private MapItUser fromUser;
    private User admin;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(userService, userRepository, notificationSender);

        fromUser = new MapItUser();
        fromUser.setId("user-1");
        fromUser.setName("Ana");
        fromUser.setEmail("ana@test.com");

        admin = new User();
        admin.setId("admin-1");
        admin.setName("Admin");
        admin.setEmail("admin@mapit-web.com");
        admin.setUserType(UserType.ADMIN);
    }

    // ── send ─────────────────────────────────────────────

    @Test
    void send_conAdminRegistrado_enviaEmailAlAdminConDatosRecortados() {
        when(userService.getByIdOrThrow("user-1")).thenReturn(fromUser);
        when(userRepository.findByUserType(UserType.ADMIN)).thenReturn(List.of(admin));

        DeveloperFeedbackRequest request = new DeveloperFeedbackRequest(
                FeedbackCategory.BUG, "  Fallo al iniciar sesión  ", "  El botón no responde  ");

        feedbackService.send("user-1", request);

        verify(notificationSender).sendDeveloperFeedbackEmail(
                "admin@mapit-web.com", "BUG", "Ana", "ana@test.com",
                "Fallo al iniciar sesión", "El botón no responde");
    }

    @Test
    void send_conVariosAdmins_envíaEmailACadaUno() {
        User otroAdmin = new User();
        otroAdmin.setId("admin-2");
        otroAdmin.setName("Admin 2");
        otroAdmin.setEmail("admin2@mapit-web.com");
        otroAdmin.setUserType(UserType.ADMIN);

        when(userService.getByIdOrThrow("user-1")).thenReturn(fromUser);
        when(userRepository.findByUserType(UserType.ADMIN)).thenReturn(List.of(admin, otroAdmin));

        DeveloperFeedbackRequest request = new DeveloperFeedbackRequest(
                FeedbackCategory.SUGGESTION, "Idea", "Sería genial que...");

        feedbackService.send("user-1", request);

        verify(notificationSender).sendDeveloperFeedbackEmail(
                eq("admin@mapit-web.com"), eq("SUGGESTION"), anyString(), anyString(), anyString(), anyString());
        verify(notificationSender).sendDeveloperFeedbackEmail(
                eq("admin2@mapit-web.com"), eq("SUGGESTION"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void send_sinAdminsRegistrados_lanzaApiException() {
        when(userService.getByIdOrThrow("user-1")).thenReturn(fromUser);
        when(userRepository.findByUserType(UserType.ADMIN)).thenReturn(List.of());

        DeveloperFeedbackRequest request = new DeveloperFeedbackRequest(
                FeedbackCategory.OTHER, "Asunto", "Mensaje");

        assertThatThrownBy(() -> feedbackService.send("user-1", request))
                .isInstanceOf(ApiException.class);

        verify(notificationSender, never())
                .sendDeveloperFeedbackEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
