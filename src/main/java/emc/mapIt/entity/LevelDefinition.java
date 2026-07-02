package emc.mapIt.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "level_definitions")
public class LevelDefinition {
    
    @Id
    private Integer level; // 0-10
    
    @NotBlank
    @Column(nullable = false)
    private String label;
    
    @NotBlank
    @Column(name = "perk_description", nullable = false, columnDefinition = "text")
    private String perkDescription;
    
    @NotNull
    @Column(name = "required_xp", nullable = false)
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
