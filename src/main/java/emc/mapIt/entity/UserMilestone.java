package emc.mapIt.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.ZonedDateTime;

@Document(collection = "user_milestones")
@CompoundIndex(name = "uk_user_milestone", def = "{'userId': 1, 'milestoneId': 1}", unique = true)
public class UserMilestone {

    @Id
    private String id;

    private String userId;

    private String milestoneId;

    private ZonedDateTime completedAt;

    // Constructores
    public UserMilestone() {}

    public UserMilestone(String userId, String milestoneId) {
        this.userId = userId;
        this.milestoneId = milestoneId;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMilestoneId() { return milestoneId; }
    public void setMilestoneId(String milestoneId) { this.milestoneId = milestoneId; }

    public ZonedDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(ZonedDateTime completedAt) { this.completedAt = completedAt; }
}
