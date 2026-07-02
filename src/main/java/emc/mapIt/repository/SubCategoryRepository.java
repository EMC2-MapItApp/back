package emc.mapIt.repository;

import emc.mapIt.entity.SubCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubCategoryRepository extends MongoRepository<SubCategory, String> {

    List<SubCategory> findByMainCategoryId(String mainCategoryId);
}
