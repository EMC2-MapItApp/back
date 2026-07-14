package emc.mapIt.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Lugar físico geolocalizado (comercio, espacio público, sede...) propiedad de un usuario, al que
 * se pueden asociar publicaciones. {@code metadata} guarda atributos libres específicos del
 * {@code locationTypeId} sin necesidad de un esquema rígido por tipo de lugar.
 */
@Document(collection = "places")
public class Place {

    @Id
    private String id;

    private String ownerId;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private String locationTypeId;

    @NotNull
    private BigDecimal lat;

    @NotNull
    private BigDecimal lng;

    private String address;

    private Map<String, Object> metadata;

    // Constructores
    public Place() {
    }

    public Place(String ownerId, String name, String locationTypeId, BigDecimal lat, BigDecimal lng) {
        this.ownerId = ownerId;
        this.name = name;
        this.locationTypeId = locationTypeId;
        this.lat = lat;
        this.lng = lng;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
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

    public String getLocationTypeId() {
        return locationTypeId;
    }

    public void setLocationTypeId(String locationTypeId) {
        this.locationTypeId = locationTypeId;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}