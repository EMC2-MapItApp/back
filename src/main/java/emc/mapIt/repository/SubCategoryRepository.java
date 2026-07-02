package emc.mapIt.repository;

import emc.mapIt.entity.SubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubCategoryRepository extends JpaRepository<SubCategory, Long> {

    List<SubCategory> findByMainCategoryId(Long mainCategoryId);
}
