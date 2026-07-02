package emc.mapIt.repository;

import emc.mapIt.entity.LocationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationTypeRepository extends MongoRepository<LocationType, String> {

    List<LocationType> findBySubCategoryId(String subCategoryId);
}