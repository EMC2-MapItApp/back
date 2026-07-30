package emc.mapIt.notifications;

import emc.mapIt.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST del centro de notificaciones y de las suscripciones push. Todas las rutas
 * requieren sesión salvo {@code GET /push/public-key} (declarada pública en
 * {@code SecurityConfig}) — el frontend la necesita para poder suscribirse justo tras el login,
 * antes de que exista ningún otro estado de sesión relevante para este módulo.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;
    private final AuthService authService;
    private final String vapidPublicKey;

    public NotificationController(
            NotificationService notificationService,
            NotificationMapper notificationMapper,
            AuthService authService,
            @Value("${mapit.push.vapid.public-key:}") String vapidPublicKey) {
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
        this.authService = authService;
        this.vapidPublicKey = vapidPublicKey;
    }

    /** Notificaciones del usuario autenticado, más recientes primero (últimas 50). */
    @GetMapping
    public List<NotificationResponse> list(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUserId(authorization);
        return notificationService.listForUser(userId).stream().map(notificationMapper::toResponse).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUserId(authorization);
        return Map.of("count", notificationService.unreadCount(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUserId(authorization);
        notificationService.markRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUserId(authorization);
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/unread")
    public ResponseEntity<Void> markUnread(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUserId(authorization);
        notificationService.markUnread(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUserId(authorization);
        notificationService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUserId(authorization);
        notificationService.deleteAll(userId);
        return ResponseEntity.noContent().build();
    }

    /** Clave pública VAPID que el frontend usa para suscribirse al {@code PushManager} del navegador. */
    @GetMapping("/push/public-key")
    public Map<String, String> pushPublicKey() {
        return Map.of("publicKey", vapidPublicKey);
    }

    @PostMapping("/push/subscriptions")
    public ResponseEntity<Void> registerPushSubscription(@Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String userId = authService.requireUserId(authorization);
        log.info("Registro de suscripción push userId={}", userId);
        notificationService.registerSubscription(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/push/subscriptions")
    public ResponseEntity<Void> unregisterPushSubscription(@Valid @RequestBody PushUnsubscribeRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        authService.requireUserId(authorization);
        notificationService.unregisterSubscription(request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
