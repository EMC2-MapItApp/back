package emc.mapIt.groups;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Acceso a {@link GroupJoinRequest}. */
@Repository
public interface GroupJoinRequestRepository extends MongoRepository<GroupJoinRequest, String> {

    boolean existsByGroupIdAndRequestedByUserIdAndStatus(
            String groupId, String requestedByUserId, GroupJoinRequestStatus status);

    List<GroupJoinRequest> findByGroupIdAndStatus(String groupId, GroupJoinRequestStatus status);
}
