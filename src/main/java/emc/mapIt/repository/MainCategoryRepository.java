package emc.mapIt.repository;

import emc.mapIt.entity.MainCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MainCategoryRepository extends MongoRepository<MainCategory, String> {
}