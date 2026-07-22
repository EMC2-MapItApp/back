package emc.mapIt.groups;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Grupo de usuarios en torno a un tema. La pertenencia (incluido el organizador) vive en
 * {@link GroupMember}, colección hermana — así "listar miembros" y "listar mis grupos" son
 * consultas directas por índice en vez de escaneos de un array embebido, igual que
 * {@code PublicationEnrollment} respecto a {@code Publication}.
 */
@Document(collection = "groups")
public class Group {

    @Id
    private String id;

    @NotBlank
    private String name;

    private String description;

    /** Id de {@code MainCategory} (árbol de categorías del mapa) que clasifica el grupo. */
    @NotBlank
    private String categoryId;

    /** Id del usuario que creó el grupo. Redundante con el {@link GroupMember} de rol ORGANIZER, pero evita un lookup extra en las comprobaciones de autorización más frecuentes. */
    @NotBlank
    private String organizerId;

    private Instant createdAt;

    private Instant updatedAt;

    public Group() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
