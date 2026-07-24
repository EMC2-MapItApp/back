package emc.mapIt.notifications;

/**
 * Fallo al enviar un push por una causa distinta a una suscripción caducada (red, credenciales
 * VAPID inválidas, error del push service, ...). A diferencia del email
 * ({@link NotificationSender}, que si falla lanza {@code ApiException} y aborta la petición), el
 * push es un canal best-effort: {@link NotificationService} captura esta excepción, la loguea y
 * continúa — nunca debe romper el flujo de negocio que la originó.
 */
public class PushDeliveryException extends RuntimeException {

    public PushDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
