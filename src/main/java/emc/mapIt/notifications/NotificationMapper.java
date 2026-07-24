package emc.mapIt.notifications;

import org.springframework.stereotype.Component;

/** Mapeo manual entidad↔DTO de {@link Notification} — sin MapStruct, mismo criterio que {@code GroupMapper}. */
@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
