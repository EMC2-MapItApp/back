package emc.mapIt.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "level_definitions")
public class LevelDefinition {

    @Id
    private Integer level; // 0-10

    @NotBlank
    private String label;

    @NotBlank
    private String perkDescription;

    @NotNull
    private Integer requiredXp; // [0,100,250,450,700,1000,1400,1900,2500,3200,4000]
    
    // Constructores
    public LevelDefinition() {}
    
    public LevelDefinition(Integer level, String label, String perkDescription, Integer requiredXp) {
        this.level = level;
        this.label = label;
        this.perkDescription = perkDescription;
        this.requiredXp = requiredXp;
    }
    
    // Getters y Setters
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    
    public String getPerkDescription() { return perkDescription; }
    public void setPerkDescription(String perkDescription) { this.perkDescription = perkDescription; }
    
    public Integer getRequiredXp() { return requiredXp; }
    public void setRequiredXp(Integer requiredXp) { this.requiredXp = requiredXp; }
}
