package emc.mapIt.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UserMilestoneId implements Serializable {
    
    private UUID userId;
    private String milestoneId;
    
    // Constructores
    public UserMilestoneId() {}
    
    public UserMilestoneId(UUID userId, String milestoneId) {
        this.userId = userId;
        this.milestoneId = milestoneId;
    }
    
    // Getters y Setters
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    
    public String getMilestoneId() { return milestoneId; }
    public void setMilestoneId(String milestoneId) { this.milestoneId = milestoneId; }
    
    // equals y hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMilestoneId that = (UserMilestoneId) o;
        return Objects.equals(userId, that.userId) && 
               Objects.equals(milestoneId, that.milestoneId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, milestoneId);
    }
}
