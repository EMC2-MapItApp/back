package emc.mapIt.entity;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "capability_definitions")
public class CapabilityDefinition {

    @Id
    private String id; // e.g. 'max_publications_5'

    @NotBlank
    private String label;

    @NotBlank
    private String description;

    private Integer unlocksAtLevel;

    private Boolean purchasable = false;

    private BigDecimal priceEur;
    
    // Constructores
    public CapabilityDefinition() {}
    
    public CapabilityDefinition(String id, String label, String description) {
        this.id = id;
        this.label = label;
        this.description = description;
    }
    
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Integer getUnlocksAtLevel() { return unlocksAtLevel; }
    public void setUnlocksAtLevel(Integer unlocksAtLevel) { this.unlocksAtLevel = unlocksAtLevel; }
    
    public Boolean getPurchasable() { return purchasable; }
    public void setPurchasable(Boolean purchasable) { this.purchasable = purchasable; }
    
    public BigDecimal getPriceEur() { return priceEur; }
    public void setPriceEur(BigDecimal priceEur) { this.priceEur = priceEur; }
}
