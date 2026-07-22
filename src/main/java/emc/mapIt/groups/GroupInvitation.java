package emc.mapIt.groups;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Invitación de un usuario existente a un {@link Group}. Sin uniqueness a nivel de BD sobre
 * {@code groupId}+{@code invitedUserId}: tras un {@code DECLINED} debe poder emitirse una nueva
 * invitación, así que la comprobación de "ya existe una invitación pendiente" vive en
 * {@link GroupService} (solo bloquea duplicados en estado {@code PENDING}).
 * <p>
 * El propio {@code id} del documento se usa como identificador en el enlace del correo de
 * invitación (query param {@code token} del frontend) — no es un secreto tipo
 * {@code EmailVerificationToken}: aceptar/rechazar siempre valida que el llamador autenticado
 * coincida con {@code invitedUserId}, igual que cualquier otro recurso con id de Mongo en esta
 * app (no hay bypass de autenticación en juego, a diferencia de la verificación de email).
 * </p>
 */
@Document(collection = "group_invitations")
public class GroupInvitation {

    @Id
    private String id;

    private String groupId;

    private String invitedUserId;

    private String invitedByUserId;

    private GroupInvitationStatus status;

    private Instant createdAt;

    private Instant respondedAt;

    public GroupInvitation() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getInvitedUserId() {
        return invitedUserId;
    }

    public void setInvitedUserId(String invitedUserId) {
        this.invitedUserId = invitedUserId;
    }

    public String getInvitedByUserId() {
        return invitedByUserId;
    }

    public void setInvitedByUserId(String invitedByUserId) {
        this.invitedByUserId = invitedByUserId;
    }

    public GroupInvitationStatus getStatus() {
        return status;
    }

    public void setStatus(GroupInvitationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }
}
