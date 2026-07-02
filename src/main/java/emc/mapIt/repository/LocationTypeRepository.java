package emc.mapIt.repository;

import emc.mapIt.entity.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationTypeRepository extends JpaRepository<LocationType, Long> {

    List<LocationType> findBySubCategoryId(Long subCategoryId);
}