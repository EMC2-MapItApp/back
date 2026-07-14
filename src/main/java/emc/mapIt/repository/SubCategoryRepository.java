package emc.mapIt.repository;

import emc.mapIt.entity.SubCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Acceso a {@link SubCategory}, nivel intermedio del árbol de categorías. */
@Repository
public interface SubCategoryRepository extends MongoRepository<SubCategory, String> {

    List<SubCategory> findByMainCategoryId(String mainCategoryId);
}
