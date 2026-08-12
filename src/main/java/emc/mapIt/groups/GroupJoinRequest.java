package emc.mapIt.groups;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Solicitud de acceso de un usuario a un {@link Group}, iniciada por el propio usuario (a
 * diferencia de {@link GroupInvitation}, que es iniciada por el organizador). Nace siempre desde
 * el contexto de una {@code Publication} privada a la que el usuario intenta apuntarse sin ser
 * miembro — {@code publicationId} queda como referencia de trazabilidad, no condiciona la
 * resolución de la solicitud (aceptarla concede pertenencia al grupo, no inscribe en ninguna
 * publicación).
 * <p>
 * Sin uniqueness a nivel de BD sobre {@code groupId}+{@code requestedByUserId}: tras un
 * {@code REJECTED} debe poder emitirse una nueva solicitud, así que la comprobación de "ya existe
 * una solicitud pendiente" vive en {@link GroupService} (solo bloquea duplicados en estado
 * {@code PENDING}) — mismo patrón que {@link GroupInvitation}.
 * </p>
 */
@Document(collection = "group_join_requests")
public class GroupJoinRequest {

    @Id
    private String id;

    private String groupId;

    private String requestedByUserId;

    /** Publicación privada que motivó la solicitud. Solo trazabilidad, nullable. */
    private String publicationId;

    private GroupJoinRequestStatus status;

    private Instant createdAt;

    private Instant respondedAt;

    public GroupJoinRequest() {
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

    public String getRequestedByUserId() {
        return requestedByUserId;
    }

    public void setRequestedByUserId(String requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
    }

    public GroupJoinRequestStatus getStatus() {
        return status;
    }

    public void setStatus(GroupJoinRequestStatus status) {
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
