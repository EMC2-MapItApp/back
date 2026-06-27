package emc.mapIt.repository;

import emc.mapIt.entity.MilestoneDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MilestoneDefinitionRepository extends JpaRepository<MilestoneDefinition, String> {
    
    /**
     * Busca hitos por tipo de condición.
     */
    List<MilestoneDefinition> findByConditionType(String conditionType);
    
    /**
     * Busca hitos por tipo de condición y valor específico.
     */
    List<MilestoneDefinition> findByConditionTypeAndConditionValue(String conditionType, Integer conditionValue);
    
    /**
     * Busca hitos que otorgan una cantidad específica de XP.
     */
    List<MilestoneDefinition> findByXpReward(Integer xpReward);
    
    /**
     * Busca hitos por rango de recompensa XP.
     */
    @Query("SELECT md FROM MilestoneDefinition md WHERE md.xpReward BETWEEN :minXp AND :maxXp")
    List<MilestoneDefinition> findByXpRewardRange(@Param("minXp") Integer minXp, @Param("maxXp") Integer maxXp);
    
    /**
     * Busca hitos por tipo de condición ordenados por valor de condición.
     */
    @Query("""
        SELECT md FROM MilestoneDefinition md 
        WHERE md.conditionType = :conditionType 
        ORDER BY md.conditionValue ASC
        """)
    List<MilestoneDefinition> findByConditionTypeOrderByValue(@Param("conditionType") String conditionType);
    
    /**
     * Busca hitos de "primera vez" (sin valor de condición específico).
     */
    @Query("SELECT md FROM MilestoneDefinition md WHERE md.conditionValue IS NULL")
    List<MilestoneDefinition> findFirstTimeMilestones();
    
    /**
     * Busca hitos disponibles para progreso basado en conteos.
     */
    @Query("""
        SELECT md FROM MilestoneDefinition md 
        WHERE md.conditionType = :conditionType 
        AND md.conditionValue <= :currentCount
        """)
    List<MilestoneDefinition> findEligibleByCount(@Param("conditionType") String conditionType, 
                                                 @Param("currentCount") Integer currentCount);
}
