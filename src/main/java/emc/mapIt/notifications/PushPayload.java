package emc.mapIt.notifications;

/**
 * Contenido mínimo de un push nativo del SO. Deliberadamente genérico (a diferencia de los
 * métodos de {@link NotificationSender}, que llevan parámetros ricos por evento): el
 * Service Worker del frontend solo sabe mostrar {@code title}/{@code body} y abrir {@code url} al
 * pulsar la notificación.
 *
 * @param title título mostrado en la notificación del SO
 * @param body  cuerpo del mensaje
 * @param url   ruta relativa del frontend a abrir al pulsar la notificación
 */
public record PushPayload(String title, String body, String url) {
}
