package emc.mapIt.notifications;

import java.time.Instant;

/** Forma pública de una {@link Notification} para el centro de notificaciones del frontend. */
public record NotificationResponse(
        String id,
        NotificationType type,
        String title,
        String body,
        String link,
        boolean read,
        Instant createdAt) {
}
