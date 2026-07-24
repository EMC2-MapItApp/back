package emc.mapIt.notifications;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Suscripción Web Push de un dispositivo/navegador concreto de un usuario. Un usuario puede tener
 * varias a la vez (móvil + escritorio) — el identificador natural es el {@code endpoint} que
 * asigna el push service del navegador, único a nivel de índice. Las claves {@code p256dh}/
 * {@code auth} las genera el navegador al suscribirse y sirven para cifrar el payload de cada
 * push (protocolo Web Push, RFC 8291); no son credenciales del usuario.
 */
@Document(collection = "push_subscriptions")
public class PushSubscription {

    @Id
    private String id;

    private String userId;

    @Indexed(unique = true)
    private String endpoint;

    private String p256dhKey;

    private String authKey;

    /** User-Agent del navegador que se suscribió; solo informativo (futuro "mis dispositivos"). */
    private String userAgent;

    private Instant createdAt;

    public PushSubscription() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getP256dhKey() {
        return p256dhKey;
    }

    public void setP256dhKey(String p256dhKey) {
        this.p256dhKey = p256dhKey;
    }

    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
