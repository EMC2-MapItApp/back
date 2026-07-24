package emc.mapIt.notifications;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Notificación in-app persistida para el centro de notificaciones (campana). Es la fuente de
 * verdad de "qué le ha pasado a este usuario" — el push del SO ({@link PushSender}) es solo un
 * canal de entrega best-effort adicional que puede fallar o no estar disponible (permiso
 * denegado, sin suscripción); esta colección no depende de que el push haya funcionado.
 */
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "ix_notification_user_created", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "ix_notification_user_read", def = "{'userId': 1, 'read': 1}")
})
public class Notification {

    @Id
    private String id;

    private String userId;

    private NotificationType type;

    private String title;

    private String body;

    /** Ruta relativa del frontend a abrir al pulsar la notificación (p. ej. {@code /groups}). */
    private String link;

    private boolean read;

    private Instant createdAt;

    private Instant readAt;

    public Notification() {
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

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
}
