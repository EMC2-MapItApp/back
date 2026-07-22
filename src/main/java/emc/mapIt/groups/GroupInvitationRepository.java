package emc.mapIt.groups;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Acceso a {@link GroupInvitation}. */
@Repository
public interface GroupInvitationRepository extends MongoRepository<GroupInvitation, String> {

    List<GroupInvitation> findByInvitedUserIdAndStatus(String invitedUserId, GroupInvitationStatus status);

    boolean existsByGroupIdAndInvitedUserIdAndStatus(String groupId, String invitedUserId, GroupInvitationStatus status);

    List<GroupInvitation> findByGroupIdAndStatus(String groupId, GroupInvitationStatus status);

    void deleteByGroupId(String groupId);
}
