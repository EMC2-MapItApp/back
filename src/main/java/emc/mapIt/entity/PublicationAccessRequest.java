package emc.mapIt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Solicitud de un usuario para apuntarse a una publicación {@code PRIVATE_GROUP} de la que no
 * tiene acceso (ni es miembro del grupo vinculado, si lo hay, ni ha sido invitado
 * individualmente). Iniciada por el propio usuario, a diferencia de {@link PublicationInvitation}
 * (iniciada por el autor). La aprueba el autor de la publicación — no el organizador de ningún
 * grupo, ya que una publicación privada "solo invitados" puede no tener grupo vinculado.
 * <p>
 * Al aceptarla, {@code PublicationService} materializa el acceso concedido como una
 * {@link PublicationInvitation} ya {@code ACCEPTED} — mismo mecanismo de acceso que una invitación
 * directa del autor, para que {@code PublicationService#enroll} no necesite un tercer camino de
 * comprobación de acceso.
 * </p>
 */
@Document(collection = "publication_access_requests")
public class PublicationAccessRequest {

    @Id
    private String id;

    private String publicationId;

    private String requestedByUserId;

    private PublicationAccessRequestStatus status;

    private Instant createdAt;

    private Instant respondedAt;

    public PublicationAccessRequest() {
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

    public String getRequestedByUserId() {
        return requestedByUserId;
    }

    public void setRequestedByUserId(String requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public PublicationAccessRequestStatus getStatus() {
        return status;
    }

    public void setStatus(PublicationAccessRequestStatus status) {
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
