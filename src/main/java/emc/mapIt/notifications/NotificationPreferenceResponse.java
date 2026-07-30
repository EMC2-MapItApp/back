package emc.mapIt.notifications;

/** Estado de la preferencia de email de un {@link NotificationType} para el usuario autenticado. */
public record NotificationPreferenceResponse(NotificationType type, boolean emailEnabled) {
}
