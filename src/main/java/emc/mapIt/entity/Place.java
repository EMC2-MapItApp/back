package emc.mapIt.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "places")
public class Place {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    
    @NotBlank
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "text")
    private String description;
    
    @NotBlank
    @Column(name = "location_type_id", nullable = false)
    private String locationTypeId;
    
    @NotNull
    @Column(precision = 10, scale = 8)
    private BigDecimal lat;
    
    @NotNull
    @Column(precision = 11, scale = 8)
    private BigDecimal lng;
    
    private String address;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    // Constructores
    public Place() {}
    
    public Place(User owner, String name, String locationTypeId, BigDecimal lat, BigDecimal lng) {
        this.owner = owner;
        this.name = name;
        this.locationTypeId = locationTypeId;
        this.lat = lat;
        this.lng = lng;
    }
    
    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getLocationTypeId() { return locationTypeId; }
    public void setLocationTypeId(String locationTypeId) { this.locationTypeId = locationTypeId; }
    
    public BigDecimal getLat() { return lat; }
    public void setLat(BigDecimal lat) { this.lat = lat; }
    
    public BigDecimal getLng() { return lng; }
    public void setLng(BigDecimal lng) { this.lng = lng; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}