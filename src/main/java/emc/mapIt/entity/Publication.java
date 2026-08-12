package emc.mapIt.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Publicación geolocalizada creada por un usuario: un {@link PublicationType#EVENT} puntual (con
 * su propia {@code lat}/{@code lng} cuando no está ligado a un {@link Place}) o una
 * {@link PublicationType#PROMOTION} asociada a un lugar existente ({@code placeId}). Varios
 * campos son nullable según el tipo — ver comentarios inline en cada campo.
 */
@Document(collection = "publications")
public class Publication {

    @Id
    private String id;

    private String authorId;

    private PublicationType publicationType;

    private String placeId; // nullable para individual events

    @NotNull
    private String locationTypeId;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private ZonedDateTime startDate;

    private ZonedDateTime endDate; // null = indefinite promotion

    private BigDecimal lat; // nullable, para individual events

    private BigDecimal lng; // nullable, para individual events

    private Integer requiredLevel = 0; // default 0 = visible to all

    private Map<String, Object> metadata;

    private Boolean active = true;

    private PublicationVisibility visibility = PublicationVisibility.PUBLIC;

    /** Id del {@code Group} al que está restringida esta publicación. Solo poblado si {@code visibility == PRIVATE_GROUP}. */
    private String groupId;

    // Constructores
    public Publication() {
    }

    public Publication(String authorId, PublicationType publicationType, String locationTypeId,
            String title, ZonedDateTime startDate) {
        this.authorId = authorId;
        this.publicationType = publicationType;
        this.locationTypeId = locationTypeId;
        this.title = title;
        this.startDate = startDate;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public PublicationType getPublicationType() {
        return publicationType;
    }

    public void setPublicationType(PublicationType publicationType) {
        this.publicationType = publicationType;
    }

    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    public String getLocationTypeId() {
        return locationTypeId;
    }

    public void setLocationTypeId(String locationTypeId) {
        this.locationTypeId = locationTypeId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public Integer getRequiredLevel() {
        return requiredLevel;
    }

    public void setRequiredLevel(Integer requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public PublicationVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(PublicationVisibility visibility) {
        this.visibility = visibility;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}