package emc.mapIt.repository;

import emc.mapIt.entity.UserProfileDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileDetailsRepository extends MongoRepository<UserProfileDetails, String> {
}