package emc.mapIt.notifications;

/**
 * Señala que una {@link PushSubscription} ya no es válida (el push service del navegador
 * respondió 404/410 — el usuario revocó el permiso, desinstaló el navegador, o el endpoint
 * caducó). {@link NotificationService} la captura para borrar la suscripción; no debe propagarse
 * como fallo del evento de negocio que disparó el push.
 */
public class PushSubscriptionExpiredException extends RuntimeException {

    public PushSubscriptionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
