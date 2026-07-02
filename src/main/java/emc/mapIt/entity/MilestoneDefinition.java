package emc.mapIt.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "milestone_definitions")
public class MilestoneDefinition {
    
    @Id
    private String id;
    
    @NotBlank
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    
    @NotNull
    @Column(name = "xp_reward", nullable = false)
    private Integer xpReward;
    
    @NotBlank
    @Column(name = "condition_type", nullable = false)
    private String conditionType;
    
    @Column(name = "condition_value")
    private Integer conditionValue;
    
    // Constructores
    public MilestoneDefinition() {}
    
    public MilestoneDefinition(String id, String description, Integer xpReward, String conditionType) {
        this.id = id;
        this.description = description;
        this.xpReward = xpReward;
        this.conditionType = conditionType;
    }
    
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Integer getXpReward() { return xpReward; }
    public void setXpReward(Integer xpReward) { this.xpReward = xpReward; }
    
    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }
    
    public Integer getConditionValue() { return conditionValue; }
    public void setConditionValue(Integer conditionValue) { this.conditionValue = conditionValue; }
}
