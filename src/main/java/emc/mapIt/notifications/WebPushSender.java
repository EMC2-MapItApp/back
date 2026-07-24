package emc.mapIt.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * Adaptador de salida (arquitectura hexagonal) del puerto {@link PushSender} vía el protocolo
 * Web Push estándar (VAPID), usando {@code nl.martijndwars:web-push}. Hermano de
 * {@link EmailNotificationSender} — misma idea de "canal de entrega intercambiable detrás de un
 * puerto", ver {@code docs/back/IDEAS.md} para el camino a Capacitor/FCM/APNs.
 * <p>
 * Si las claves VAPID no están configuradas (dev sin variables exportadas), el adaptador queda
 * inactivo y {@link #send} falla con {@link PushDeliveryException} en vez de impedir arrancar la
 * aplicación — mismo criterio "fail-soft en dev" que {@link EmailNotificationSender} aplica para
 * SMTP vacío.
 * </p>
 */
@Component
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public WebPushSender(
            ObjectMapper objectMapper,
            @Value("${mapit.push.vapid.public-key:}") String publicKey,
            @Value("${mapit.push.vapid.private-key:}") String privateKey,
            @Value("${mapit.push.vapid.subject:}") String subject) {
        this.objectMapper = objectMapper;

        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.warn("mapit.push.vapid.public-key/private-key vacíos — el envío de push nativo quedará "
                    + "deshabilitado (los eventos de grupo seguirán notificando por email y en el centro "
                    + "in-app). Genera un par de claves VAPID y exporta MAPIT_VAPID_PUBLIC_KEY/"
                    + "MAPIT_VAPID_PRIVATE_KEY para activarlo.");
            this.pushService = null;
            return;
        }

        PushService configured;
        try {
            configured = new PushService(publicKey, privateKey, subject);
        } catch (GeneralSecurityException e) {
            log.error("Claves VAPID inválidas — push nativo deshabilitado", e);
            configured = null;
        }
        this.pushService = configured;
    }

    @Override
    public void send(PushSubscription subscription, PushPayload payload) {
        if (pushService == null) {
            throw new PushDeliveryException("Push no configurado (faltan claves VAPID)", null);
        }

        try {
            Subscription.Keys keys = new Subscription.Keys(subscription.getP256dhKey(), subscription.getAuthKey());
            Subscription webPushSubscription = new Subscription(subscription.getEndpoint(), keys);
            nl.martijndwars.webpush.Notification notification =
                    new nl.martijndwars.webpush.Notification(webPushSubscription, toJson(payload));

            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();
            // TODO(debug-push): log temporal — quitar una vez confirmado el flujo end-to-end.
            log.info("Push service respondió HTTP {} para endpoint={}", status, subscription.getEndpoint());

            if (status == 404 || status == 410) {
                throw new PushSubscriptionExpiredException(
                        "Suscripción caducada (HTTP " + status + ") endpoint=" + subscription.getEndpoint(), null);
            }
            if (status >= 300) {
                throw new PushDeliveryException("Push service respondió HTTP " + status, null);
            }
        } catch (PushSubscriptionExpiredException | PushDeliveryException e) {
            throw e;
        } catch (Exception e) {
            // Log con causa completa: PushDeliveryException solo expone el mensaje genérico hacia
            // arriba, así que sin esto la causa real (crypto, red, VAPID mal configurado...) se pierde.
            log.warn("Fallo al enviar push a endpoint={}", subscription.getEndpoint(), e);
            throw new PushDeliveryException("Fallo al enviar push a endpoint=" + subscription.getEndpoint(), e);
        }
    }

    private String toJson(PushPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new PushDeliveryException("No se pudo serializar el payload de push", e);
        }
    }
}
