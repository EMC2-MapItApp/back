package emc.mapIt.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link WebPushSender} construye internamente su propio {@code PushService} (SDK del protocolo
 * Web Push) a partir de las claves VAPID inyectadas — no es sustituible por un mock de la forma
 * en que {@code EmailNotificationSender} recibe {@code JavaMailSender} ya construido. Estos tests
 * cubren los dos caminos que no requieren red real: claves ausentes (adaptador inactivo) y un
 * payload con clave de suscriptor inválida (fallo de cifrado, antes de cualquier llamada HTTP).
 */
class WebPushSenderTest {

    private static String vapidPublicKey;
    private static String vapidPrivateKey;

    @BeforeAll
    static void generateVapidKeyPair() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        vapidPublicKey = encoder.encodeToString(Utils.encode((ECPublicKey) keyPair.getPublic()));
        vapidPrivateKey = encoder.encodeToString(Utils.encode((ECPrivateKey) keyPair.getPrivate()));
    }

    @Test
    void send_sinClavesVapidConfiguradas_lanzaPushDeliveryExceptionSinIntentarRed() {
        WebPushSender sender = new WebPushSender(new ObjectMapper(), "", "", "mailto:test@mapit.local");

        PushSubscription subscription = subscription();
        PushPayload payload = new PushPayload("Título", "Cuerpo", "/groups");

        assertThatThrownBy(() -> sender.send(subscription, payload))
                .isInstanceOf(PushDeliveryException.class);
    }

    @Test
    void send_conClaveDeSuscriptorInvalida_envuelveElFalloEnPushDeliveryException() {
        WebPushSender sender = new WebPushSender(
                new ObjectMapper(), vapidPublicKey, vapidPrivateKey, "mailto:test@mapit.local");

        PushSubscription subscription = subscription();
        subscription.setP256dhKey("no-es-una-clave-ec-valida");
        PushPayload payload = new PushPayload("Título", "Cuerpo", "/groups");

        assertThatThrownBy(() -> sender.send(subscription, payload))
                .isInstanceOf(PushDeliveryException.class);
    }

    private PushSubscription subscription() {
        PushSubscription subscription = new PushSubscription();
        subscription.setUserId("user-1");
        subscription.setEndpoint("https://push.example.com/endpoint");
        subscription.setP256dhKey("p256dh-fake");
        subscription.setAuthKey("auth-fake");
        return subscription;
    }
}
