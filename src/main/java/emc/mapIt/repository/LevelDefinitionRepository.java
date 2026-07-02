package emc.mapIt.repository;

import emc.mapIt.entity.LevelDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevelDefinitionRepository extends MongoRepository<LevelDefinition, Integer> {

    /**
     * Busca la definición de nivel máximo alcanzable con ese XP.
     */
    @Query("{requiredXp: {$lte: ?0}}")
    List<LevelDefinition> findLevelsUpToXp(Integer currentXp);

    /**
     * Busca el siguiente nivel disponible.
     */
    Optional<LevelDefinition> findFirstByLevelGreaterThanOrderByLevelAsc(Integer currentLevel);

    /**
     * Obtiene todos los niveles ordenados por nivel ascendente.
     */
    List<LevelDefinition> findAllByOrderByLevelAsc();

    /**
     * Busca niveles hasta un máximo específico.
     */
    List<LevelDefinition> findByLevelLessThanEqualOrderByLevelAsc(Integer maxLevel);
}