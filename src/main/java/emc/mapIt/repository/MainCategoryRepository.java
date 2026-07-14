package emc.mapIt.repository;

import emc.mapIt.entity.MainCategory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Acceso a {@link MainCategory}, raíz del árbol de categorías. Solo necesita el CRUD heredado. */
@Repository
public interface MainCategoryRepository extends MongoRepository<MainCategory, String> {
}