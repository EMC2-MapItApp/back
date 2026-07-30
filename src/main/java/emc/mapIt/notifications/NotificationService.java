package emc.mapIt.notifications;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orquesta, por evento de negocio, los canales de notificación aplicables: email (
 * {@link NotificationSender}, sin cambios de comportamiento), centro in-app persistido (
 * {@link Notification}) y push nativo del SO ({@link PushSender}). Es la pieza que sustituye el
 * uso directo de {@link NotificationSender} en {@code GroupService} — el dominio Grupos deja de
 * conocer qué canales existen, solo invoca "notifica este evento".
 * <p>
 * El email es el único canal crítico: si falla, {@link EmailNotificationSender} lanza
 * {@link ApiException} y el caso de uso de negocio se aborta, igual que antes de este cambio. El
 * centro in-app y el push son best-effort — un fallo de push nunca debe impedir que la
 * invitación/aviso/difusión se considere enviada.
 * </p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Últimas notificaciones devueltas por {@link #listForUser}; suficiente para un centro de notificaciones sin paginación real. */
    private static final int LIST_LIMIT = 50;

    private final NotificationSender notificationSender;
    private final PushSender pushSender;
    private final NotificationRepository notificationRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;

    public NotificationService(
            NotificationSender notificationSender,
            PushSender pushSender,
            NotificationRepository notificationRepository,
            PushSubscriptionRepository pushSubscriptionRepository) {
        this.notificationSender = notificationSender;
        this.pushSender = pushSender;
        this.notificationRepository = notificationRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    // ── Eventos de negocio (dominio Grupos) ─────────────────────────────────────

    /** Invitación a un grupo, a un usuario ya registrado. */
    public void notifyGroupInvitation(MapItUser invited, String groupName, MapItUser invitedBy, String invitationId) {
        notificationSender.sendGroupInvitationEmail(
                invited.getEmail(), invited.getName(), groupName, invitedBy.getName(), invitationId);

        dispatch(invited.getId(), NotificationType.GROUP_INVITATION,
                "Invitación a " + groupName,
                invitedBy.getName() + " te ha invitado a unirte a \"" + groupName + "\"",
                "/groups");
    }

    /**
     * Invitación por email a alguien sin cuenta todavía. Sin {@code userId} al que asociar una
     * notificación in-app o un push — se queda solo-email, igual que antes de este cambio (ver
     * {@code GroupInvitation} para cómo se reclama automáticamente al registrarse).
     */
    public void notifyGroupSignupInvitationEmail(String email, String groupName, String invitedByName) {
        notificationSender.sendGroupSignupInvitationEmail(email, groupName, invitedByName);
    }

    /** Aviso de un miembro al organizador del grupo. */
    public void notifyGroupOrganizerNotice(MapItUser organizer, String groupName, MapItUser fromUser,
            String subject, String message) {
        notificationSender.sendGroupOrganizerNoticeEmail(
                organizer.getEmail(), organizer.getName(), groupName, fromUser.getName(), subject, message);

        dispatch(organizer.getId(), NotificationType.GROUP_ORGANIZER_NOTICE,
                "Aviso en " + groupName,
                fromUser.getName() + ": " + subject,
                "/groups");
    }

    /** Difusión del organizador a un miembro del grupo (se invoca una vez por destinatario). */
    public void notifyGroupBroadcast(MapItUser recipient, String groupName, MapItUser organizer,
            String subject, String message) {
        notificationSender.sendGroupBroadcastEmail(
                recipient.getEmail(), recipient.getName(), groupName, organizer.getName(), subject, message);

        dispatch(recipient.getId(), NotificationType.GROUP_BROADCAST,
                groupName + ": " + subject,
                message,
                "/groups");
    }

    // ── Centro de notificaciones in-app ─────────────────────────────────────────

    public List<Notification> listForUser(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, LIST_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    public void markRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Notificación no encontrada", HttpStatus.NOT_FOUND));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
    }

    public void markAllRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalse(userId);
        Instant now = Instant.now();
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(now);
        });
        notificationRepository.saveAll(unread);
    }

    public void markUnread(String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Notificación no encontrada", HttpStatus.NOT_FOUND));
        if (notification.isRead()) {
            notification.setRead(false);
            notification.setReadAt(null);
            notificationRepository.save(notification);
        }
    }

    public void delete(String userId, String notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Notificación no encontrada", HttpStatus.NOT_FOUND));
        notificationRepository.delete(notification);
    }

    public void deleteAll(String userId) {
        notificationRepository.deleteByUserId(userId);
    }

    // ── Suscripciones push ───────────────────────────────────────────────────────

    public void registerSubscription(String userId, PushSubscriptionRequest request) {
        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);

        // El navegador solo mantiene una suscripción activa por dispositivo (no por usuario): en
        // uno compartido, el siguiente que active el push aquí "hereda" el endpoint del anterior.
        // El logout ya intenta dar de baja la suscripción antes de esto (ver
        // HomeShellComponent.logout), pero si el usuario cierra la pestaña sin cerrar sesión este
        // log es la única pista de que ocurrió una reasignación.
        String previousUserId = subscription.getUserId();
        if (previousUserId != null && !previousUserId.equals(userId)) {
            log.info("Suscripción push reasignada endpoint={} de userId={} a userId={} (dispositivo compartido)",
                    request.endpoint(), previousUserId, userId);
        }

        subscription.setUserId(userId);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dhKey(request.keys().p256dh());
        subscription.setAuthKey(request.keys().auth());
        if (subscription.getCreatedAt() == null) {
            subscription.setCreatedAt(Instant.now());
        }
        pushSubscriptionRepository.save(subscription);
        log.info("Suscripción push registrada userId={} endpoint={}", userId, request.endpoint());
    }

    public void unregisterSubscription(String endpoint) {
        pushSubscriptionRepository.deleteByEndpoint(endpoint);
    }

    // ── Helpers privados ─────────────────────────────────────────────────────────

    /** Persiste la notificación in-app y hace fan-out del push a todas las suscripciones del usuario. */
    private void dispatch(String userId, NotificationType type, String title, String body, String link) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notification.setRead(false);
        notification.setCreatedAt(Instant.now());
        notificationRepository.save(notification);

        PushPayload payload = new PushPayload(title, body, link);
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserId(userId);
        // TODO(debug-push): log temporal para diagnosticar el flujo de push en dev — quitar una
        // vez confirmado que la entrega funciona de extremo a extremo.
        log.info("Fan-out de push userId={} suscripcionesEncontradas={}", userId, subscriptions.size());
        subscriptions.forEach(subscription -> {
            try {
                pushSender.send(subscription, payload);
                log.info("Push enviado OK endpoint={}", subscription.getEndpoint());
            } catch (PushSubscriptionExpiredException e) {
                log.info("Suscripción push caducada, se elimina endpoint={}", subscription.getEndpoint());
                pushSubscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
            } catch (PushDeliveryException e) {
                log.warn("No se pudo entregar el push a endpoint={}: {}", subscription.getEndpoint(), e.getMessage());
            }
        });
    }
}
