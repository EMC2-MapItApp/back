package emc.mapIt.repository;

import emc.mapIt.entity.LocationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Acceso a {@link LocationType}, hoja del árbol de categorías. */
@Repository
public interface LocationTypeRepository extends MongoRepository<LocationType, String> {

    List<LocationType> findBySubCategoryId(String subCategoryId);
}