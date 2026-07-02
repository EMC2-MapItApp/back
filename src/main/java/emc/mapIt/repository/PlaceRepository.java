package emc.mapIt.repository;

import emc.mapIt.entity.Place;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceRepository extends MongoRepository<Place, String> {

        List<Place> findByOwnerId(String ownerId);

        List<Place> findByLocationTypeId(Long locationTypeId);

        List<Place> findByNameContainingIgnoreCase(String name);
}
