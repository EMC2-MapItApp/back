package emc.mapIt.notifications;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Orquesta, por evento de negocio, los canales de notificación aplicables: email (
 * {@link NotificationSender}), centro in-app persistido ({@link Notification}) y push nativo del
 * SO ({@link PushSender}). Es la pieza que sustituye el uso directo de {@link NotificationSender}
 * en {@code GroupService} — el dominio Grupos deja de conocer qué canales existen, solo invoca
 * "notifica este evento".
 * <p>
 * El email es condicional a la preferencia por tipo del destinatario ({@link
 * NotificationPreference}, ver {@link #isEmailEnabled}), pero sigue siendo el único canal crítico
 * cuando se intenta: si falla, {@link EmailNotificationSender} lanza {@link ApiException} y el
 * caso de uso de negocio se aborta, igual que antes de este cambio. El centro in-app **no** es
 * configurable — se persiste siempre, sin excepción. El push es best-effort y además se apaga
 * globalmente vía {@code mapit.push.enabled} (no es una preferencia por usuario, es un
 * interruptor de producto) — un fallo o apagado de push nunca debe impedir que la invitación/
 * aviso/difusión se considere enviada.
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
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final boolean pushEnabled;

    public NotificationService(
            NotificationSender notificationSender,
            PushSender pushSender,
            NotificationRepository notificationRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            @Value("${mapit.push.enabled:true}") boolean pushEnabled) {
        this.notificationSender = notificationSender;
        this.pushSender = pushSender;
        this.notificationRepository = notificationRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.pushEnabled = pushEnabled;
    }

    // ── Eventos de negocio (dominio Grupos) ─────────────────────────────────────

    /** Invitación a un grupo, a un usuario ya registrado. */
    public void notifyGroupInvitation(MapItUser invited, String groupName, MapItUser invitedBy, String invitationId) {
        if (isEmailEnabled(invited.getId(), NotificationType.GROUP_INVITATION)) {
            notificationSender.sendGroupInvitationEmail(
                    invited.getEmail(), invited.getName(), groupName, invitedBy.getName(), invitationId);
        }

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
        if (isEmailEnabled(organizer.getId(), NotificationType.GROUP_ORGANIZER_NOTICE)) {
            notificationSender.sendGroupOrganizerNoticeEmail(
                    organizer.getEmail(), organizer.getName(), groupName, fromUser.getName(), subject, message);
        }

        dispatch(organizer.getId(), NotificationType.GROUP_ORGANIZER_NOTICE,
                "Aviso en " + groupName,
                fromUser.getName() + ": " + subject,
                "/groups");
    }

    /** Difusión del organizador a un miembro del grupo (se invoca una vez por destinatario). */
    public void notifyGroupBroadcast(MapItUser recipient, String groupName, MapItUser organizer,
            String subject, String message) {
        if (isEmailEnabled(recipient.getId(), NotificationType.GROUP_BROADCAST)) {
            notificationSender.sendGroupBroadcastEmail(
                    recipient.getEmail(), recipient.getName(), groupName, organizer.getName(), subject, message);
        }

        dispatch(recipient.getId(), NotificationType.GROUP_BROADCAST,
                groupName + ": " + subject,
                message,
                "/groups");
    }

    /** Invitación individual a un evento (publicación), independiente de su visibilidad. */
    public void notifyPublicationInvitation(MapItUser invited, String publicationTitle, MapItUser invitedBy) {
        if (isEmailEnabled(invited.getId(), NotificationType.PUBLICATION_INVITATION)) {
            notificationSender.sendPublicationInvitationEmail(
                    invited.getEmail(), invited.getName(), publicationTitle, invitedBy.getName());
        }

        dispatch(invited.getId(), NotificationType.PUBLICATION_INVITATION,
                "Invitación a \"" + publicationTitle + "\"",
                invitedBy.getName() + " te ha invitado al evento \"" + publicationTitle + "\"",
                "/");
    }

    /** Alguien ha solicitado apuntarse a una publicación privada — se notifica al autor. */
    public void notifyPublicationAccessRequest(MapItUser author, String publicationTitle, MapItUser requester) {
        if (isEmailEnabled(author.getId(), NotificationType.PUBLICATION_ACCESS_REQUEST)) {
            notificationSender.sendPublicationAccessRequestEmail(
                    author.getEmail(), author.getName(), publicationTitle, requester.getName());
        }

        dispatch(author.getId(), NotificationType.PUBLICATION_ACCESS_REQUEST,
                "Solicitud para \"" + publicationTitle + "\"",
                requester.getName() + " quiere apuntarse a \"" + publicationTitle + "\"",
                "/profile");
    }

    /** Una solicitud de acceso a una publicación privada ha sido aceptada o rechazada. */
    public void notifyPublicationAccessRequestResolved(MapItUser requester, String publicationTitle, boolean accepted) {
        if (isEmailEnabled(requester.getId(), NotificationType.PUBLICATION_ACCESS_REQUEST_RESOLVED)) {
            notificationSender.sendPublicationAccessRequestResolvedEmail(
                    requester.getEmail(), requester.getName(), publicationTitle, accepted);
        }

        dispatch(requester.getId(), NotificationType.PUBLICATION_ACCESS_REQUEST_RESOLVED,
                "Solicitud " + (accepted ? "aceptada" : "rechazada"),
                "Tu solicitud para apuntarte a \"" + publicationTitle + "\" ha sido "
                        + (accepted ? "aceptada" : "rechazada"),
                "/");
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

    // ── Preferencias de email por tipo ───────────────────────────────────────────

    /** Estado de la preferencia de email de cada {@link NotificationType} para el usuario. */
    public List<NotificationPreferenceResponse> getPreferences(String userId) {
        return Arrays.stream(NotificationType.values())
                .map(type -> new NotificationPreferenceResponse(type, isEmailEnabled(userId, type)))
                .toList();
    }

    /** Activa/desactiva el email de un {@link NotificationType} concreto para el usuario. */
    public void updateEmailPreference(String userId, NotificationType type, boolean enabled) {
        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setUserId(userId);
                    return created;
                });
        if (enabled) {
            preference.getMutedEmailTypes().remove(type);
        } else {
            preference.getMutedEmailTypes().add(type);
        }
        notificationPreferenceRepository.save(preference);
    }

    /** Un tipo ausente de {@code mutedEmailTypes} (o sin documento de preferencias) está activado por defecto. */
    private boolean isEmailEnabled(String userId, NotificationType type) {
        return notificationPreferenceRepository.findByUserId(userId)
                .map(preference -> !preference.getMutedEmailTypes().contains(type))
                .orElse(true);
    }

    // ── Helpers privados ─────────────────────────────────────────────────────────

    /**
     * Persiste la notificación in-app (siempre, sin condición) y, si {@code mapit.push.enabled}
     * lo permite, hace fan-out del push a todas las suscripciones del usuario.
     */
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

        if (!pushEnabled) {
            return;
        }

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
