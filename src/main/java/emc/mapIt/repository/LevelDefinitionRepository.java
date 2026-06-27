package emc.mapIt.repository;

import emc.mapIt.entity.LevelDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevelDefinitionRepository extends JpaRepository<LevelDefinition, Integer> {
    
    /**
     * Busca la definición de nivel por XP acumulado.
     * Retorna el nivel máximo que se puede alcanzar con ese XP.
     */
    @Query(value = """
        SELECT * FROM level_definitions ld 
        WHERE ld.required_xp <= :currentXp 
        ORDER BY ld.level DESC 
        LIMIT 1
        """, nativeQuery = true)
    Optional<LevelDefinition> findByXp(@Param("currentXp") Integer currentXp);
    
    /**
     * Busca el siguiente nivel disponible.
     */
    @Query(value = """
        SELECT * FROM level_definitions ld 
        WHERE ld.level > :currentLevel 
        ORDER BY ld.level ASC 
        LIMIT 1
        """, nativeQuery = true)
    Optional<LevelDefinition> findNextLevel(@Param("currentLevel") Integer currentLevel);
    
    /**
     * Obtiene todos los niveles ordenados por nivel ascendente.
     */
    List<LevelDefinition> findAllByOrderByLevelAsc();
    
    /**
     * Busca niveles hasta un máximo específico.
     */
    List<LevelDefinition> findByLevelLessThanEqualOrderByLevelAsc(Integer maxLevel);
    
    /**
     * Calcula el XP restante para el siguiente nivel.
     */
    @Query(value = """
        SELECT (ld.required_xp - :currentXp) as remaining_xp
        FROM level_definitions ld 
        WHERE ld.level > :currentLevel 
        ORDER BY ld.level ASC 
        LIMIT 1
        """, nativeQuery = true)
    Optional<Integer> findXpToNextLevel(@Param("currentLevel") Integer currentLevel, 
                                      @Param("currentXp") Integer currentXp);
}