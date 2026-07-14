package emc.mapIt.repository;

import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByNick(String nick);

    boolean existsByNick(String nick);

    List<User> findByUserType(UserType userType);

    List<User> findByProfileDetailsLevel(Integer level);

    List<User> findByUnlockedCapabilities(String capability);

    List<User> findByFavoriteLocationTypeIds(Long locationTypeId);

    List<User> findByNameContainingIgnoreCase(String name);

    List<User> findByProfileDetailsXpGreaterThanOrderByProfileDetailsXpDesc(Integer minXp);

    long countByUserType(UserType userType);

    Optional<User> findById(String id);
}