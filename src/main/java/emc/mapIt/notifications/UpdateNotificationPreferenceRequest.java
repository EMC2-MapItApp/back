package emc.mapIt.notifications;

import jakarta.validation.constraints.NotNull;

/** Payload para activar/desactivar el email de un {@link NotificationType} concreto. */
public record UpdateNotificationPreferenceRequest(@NotNull Boolean emailEnabled) {
}
