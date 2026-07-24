package emc.mapIt.notifications;

/**
 * Puerto (arquitectura hexagonal) para el canal de notificación nativa del SO — hermano de
 * {@link NotificationSender} (email), pero con un contrato de payload distinto (genérico,
 * {@link PushPayload}, en vez de los métodos ricos por evento del email) y semántica
 * best-effort: un fallo aquí no debe abortar el caso de uso de negocio que lo originó, a
 * diferencia de un fallo de email.
 * <p>
 * Hoy solo lo implementa {@link WebPushSender} (protocolo Web Push estándar vía VAPID). Un canal
 * futuro (p. ej. FCM/APNs cuando se empaquete con Capacitor) se añadiría como un nuevo adaptador
 * de este mismo puerto — ver {@code docs/back/IDEAS.md}.
 * </p>
 */
public interface PushSender {

    /**
     * Envía un push a un dispositivo/navegador suscrito.
     *
     * @param subscription suscripción del dispositivo destino
     * @param payload      contenido a mostrar
     * @throws PushSubscriptionExpiredException si la suscripción ya no es válida (404/410) —
     *                                           el llamador debe borrarla
     * @throws PushDeliveryException            para cualquier otro fallo de entrega
     */
    void send(PushSubscription subscription, PushPayload payload);
}
