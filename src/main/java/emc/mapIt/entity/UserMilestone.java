package emc.mapIt.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_milestones")
@IdClass(UserMilestoneId.class)
public class UserMilestone {
    
    @Id
    @Column(name = "user_id")
    private UUID userId;
    
    @Id
    @Column(name = "milestone_id")
    private String milestoneId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", insertable = false, updatable = false)
    private MilestoneDefinition milestoneDefinition;
    
    @CreationTimestamp
    @Column(name = "completed_at", nullable = false)
    private ZonedDateTime completedAt;
    
    // Constructores
    public UserMilestone() {}
    
    public UserMilestone(UUID userId, String milestoneId) {
        this.userId = userId;
        this.milestoneId = milestoneId;
    }
    
    public UserMilestone(User user, MilestoneDefinition milestoneDefinition) {
        this.userId = user.getId();
        this.milestoneId = milestoneDefinition.getId();
        this.user = user;
        this.milestoneDefinition = milestoneDefinition;
    }
    
    // Getters y Setters
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    
    public String getMilestoneId() { return milestoneId; }
    public void setMilestoneId(String milestoneId) { this.milestoneId = milestoneId; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public MilestoneDefinition getMilestoneDefinition() { return milestoneDefinition; }
    public void setMilestoneDefinition(MilestoneDefinition milestoneDefinition) { 
        this.milestoneDefinition = milestoneDefinition; 
    }
    
    public ZonedDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(ZonedDateTime completedAt) { this.completedAt = completedAt; }
}
