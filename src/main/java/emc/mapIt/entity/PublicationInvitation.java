package emc.mapIt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Invitación individual de un usuario a una {@link Publication}, independiente de la
 * visibilidad de esta. En una publicación {@code PRIVATE} es el único mecanismo de acceso para
 * apuntarse (ver {@code PublicationService#enroll}) — invitar a los integrantes de un grupo es
 * solo un atajo de cliente para crear varias invitaciones individuales de una vez, sin ningún
 * vínculo posterior con el grupo. En una publicación {@code PUBLIC} es solo un aviso, ya que
 * cualquiera puede apuntarse sin invitación.
 */
@Document(collection = "publication_invitations")
public class PublicationInvitation {

    @Id
    private String id;

    private String publicationId;

    private String invitedUserId;

    private String invitedByUserId;

    private PublicationInvitationStatus status;

    private Instant createdAt;

    private Instant respondedAt;

    public PublicationInvitation() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPublicationId() {
        return publicationId;
    }

    public void setPublicationId(String publicationId) {
        this.publicationId = publicationId;
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

    public PublicationInvitationStatus getStatus() {
        return status;
    }

    public void setStatus(PublicationInvitationStatus status) {
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
