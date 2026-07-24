package emc.mapIt.notifications;

import jakarta.validation.constraints.NotBlank;

/** Payload para desregistrar una suscripción push por su {@code endpoint}. */
public record PushUnsubscribeRequest(@NotBlank String endpoint) {
}
