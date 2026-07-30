package emc.mapIt.notifications;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

/**
 * Preferencias de notificación por email de un usuario. Solo guarda los tipos
 * {@link NotificationType} que el usuario ha silenciado explícitamente — un tipo ausente de
 * {@code mutedEmailTypes} se considera activado. Esto hace que un tipo nuevo que se añada al
 * enum quede activado por defecto para todos los usuarios existentes, sin necesidad de migrar
 * documentos (mismo criterio que {@code User.favoriteLocationTypeIds}).
 * <p>
 * El centro in-app y el push nativo no son configurables por tipo: el in-app se recibe siempre,
 * y el push se apaga/enciende globalmente vía {@code mapit.push.enabled} (ver
 * {@link NotificationService}), no por preferencia de usuario.
 */
@Document(collection = "notification_preferences")
public class NotificationPreference {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private Set<NotificationType> mutedEmailTypes = new HashSet<>();

    public NotificationPreference() {
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

    public Set<NotificationType> getMutedEmailTypes() {
        return mutedEmailTypes;
    }

    public void setMutedEmailTypes(Set<NotificationType> mutedEmailTypes) {
        this.mutedEmailTypes = mutedEmailTypes == null ? new HashSet<>() : mutedEmailTypes;
    }
}
