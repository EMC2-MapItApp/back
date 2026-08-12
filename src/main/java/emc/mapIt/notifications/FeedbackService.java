package emc.mapIt.notifications;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.UserRepository;
import emc.mapIt.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Envía el feedback (bug/sugerencia/otro) de un usuario a todas las cuentas {@link UserType#ADMIN}
 * existentes, vía email. Depende directamente del puerto {@link NotificationSender} (como
 * {@code EmailVerificationService}/{@code PasswordResetService}), no de {@link NotificationService},
 * porque este email no tiene contrapartida en el centro de notificaciones in-app de nadie.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final NotificationSender notificationSender;

    public FeedbackService(UserService userService, UserRepository userRepository,
            NotificationSender notificationSender) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.notificationSender = notificationSender;
    }

    /**
     * Envía el feedback de {@code requesterId} a todas las cuentas administradoras. El remitente
     * (nombre/email) se resuelve a partir del usuario autenticado, nunca del body del request, para
     * que no pueda falsificarse de cara al admin que lo lee.
     *
     * @param requesterId id del usuario autenticado que envía el feedback
     * @param request     categoría, asunto y mensaje
     * @throws ApiException con código NO_ADMIN_AVAILABLE si no hay ninguna cuenta ADMIN a la que enviarlo
     */
    public void send(String requesterId, DeveloperFeedbackRequest request) {
        MapItUser fromUser = userService.getByIdOrThrow(requesterId);
        List<User> admins = userRepository.findByUserType(UserType.ADMIN);
        if (admins.isEmpty()) {
            throw new ApiException("NO_ADMIN_AVAILABLE",
                    "No hay ninguna cuenta de administración disponible para recibir el feedback.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        for (User admin : admins) {
            notificationSender.sendDeveloperFeedbackEmail(
                    admin.getEmail(), request.category().name(),
                    fromUser.getName(), fromUser.getEmail(),
                    request.subject().trim(), request.message().trim());
        }

        log.info("Feedback enviado categoria={} fromUserId={} adminsNotificados={}",
                request.category(), requesterId, admins.size());
    }
}
