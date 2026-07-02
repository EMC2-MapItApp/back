package emc.mapIt.repository;

import emc.mapIt.entity.UserMilestone;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface UserMilestoneRepository extends MongoRepository<UserMilestone, String> {

    List<UserMilestone> findByUserId(String userId);

    List<UserMilestone> findByUserIdOrderByCompletedAtDesc(String userId);

    boolean existsByUserIdAndMilestoneId(String userId, String milestoneId);

    @Query("{userId: ?0, completedAt: {$gte: ?1, $lte: ?2}}")
    List<UserMilestone> findByUserIdAndDateRange(String userId, ZonedDateTime startDate, ZonedDateTime endDate);

    int countByUserId(String userId);

    List<UserMilestone> findTop10ByUserIdOrderByCompletedAtDesc(String userId);

    @Query(value = "{milestoneId: ?0}", fields = "{userId: 1}")
    List<UserMilestone> findByMilestoneId(String milestoneId);
}
