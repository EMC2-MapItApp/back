package emc.mapIt.notifications;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de registro de una suscripción push — misma forma que entrega
 * {@code PushSubscription.toJSON()} en el navegador ({@code endpoint} + {@code keys.p256dh}/
 * {@code keys.auth}).
 */
public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotNull @Valid Keys keys) {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {
    }
}
