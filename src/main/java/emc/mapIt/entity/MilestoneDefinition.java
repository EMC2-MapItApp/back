package emc.mapIt.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Definición de un hito de gamificación que otorga XP al cumplirse una condición
 * ({@code conditionType} + {@code conditionValue}, p. ej. "publicaciones creadas ≥ 5"). El
 * cumplimiento por usuario se registra en {@link UserMilestone}. Parte del sistema de
 * niveles/XP en progreso.
 */
@Document(collection = "milestone_definitions")
public class MilestoneDefinition {

    @Id
    private String id;

    @NotBlank
    private String description;

    @NotNull
    private Integer xpReward;

    @NotBlank
    private String conditionType;

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
