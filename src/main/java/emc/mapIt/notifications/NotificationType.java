package emc.mapIt.notifications;

/**
 * Tipo de evento de negocio que originó una {@link Notification}. Determina el icono/acción que
 * usa el frontend y, indirectamente, el {@code link} de destino construido por
 * {@link NotificationService}.
 */
public enum NotificationType {
    GROUP_INVITATION,
    GROUP_ORGANIZER_NOTICE,
    GROUP_BROADCAST,
    GROUP_JOIN_REQUEST,
    GROUP_JOIN_REQUEST_RESOLVED,
    PUBLICATION_INVITATION,
    PUBLICATION_ACCESS_REQUEST,
    PUBLICATION_ACCESS_REQUEST_RESOLVED
}
