package emc.mapIt.groups;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Pertenencia de un usuario a un {@link Group} (organizador incluido, con {@link GroupRole#ORGANIZER}).
 * El índice compuesto único ({@code groupId} + {@code userId}) impide duplicar membresía a nivel
 * de base de datos, mismo patrón que {@code PublicationEnrollment}.
 */
@Document(collection = "group_members")
@CompoundIndex(name = "uk_group_member", def = "{'groupId': 1, 'userId': 1}", unique = true)
public class GroupMember {

    @Id
    private String id;

    private String groupId;

    private String userId;

    private GroupRole role;

    private Instant joinedAt;

    public GroupMember() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public GroupRole getRole() {
        return role;
    }

    public void setRole(GroupRole role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}
