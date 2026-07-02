package emc.mapIt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.ZonedDateTime;

@Document(collection = "publication_enrollments")
@CompoundIndex(name = "uk_enrollment", def = "{'publicationId': 1, 'userId': 1}", unique = true)
public class PublicationEnrollment {

    @Id
    private String id;

    private String publicationId;

    private String userId;

    private ZonedDateTime createdAt;

    public PublicationEnrollment() {
    }

    public PublicationEnrollment(String publicationId, String userId, ZonedDateTime createdAt) {
        this.publicationId = publicationId;
        this.userId = userId;
        this.createdAt = createdAt;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}