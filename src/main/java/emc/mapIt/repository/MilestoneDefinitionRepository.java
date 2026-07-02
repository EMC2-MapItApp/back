package emc.mapIt.repository;

import emc.mapIt.entity.MilestoneDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MilestoneDefinitionRepository extends MongoRepository<MilestoneDefinition, String> {

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
    List<MilestoneDefinition> findByXpRewardBetween(Integer minXp, Integer maxXp);

    /**
     * Busca hitos por tipo de condición ordenados por valor de condición.
     */
    List<MilestoneDefinition> findByConditionTypeOrderByConditionValueAsc(String conditionType);

    /**
     * Busca hitos de "primera vez" (sin valor de condición específico).
     */
    @Query("{conditionValue: null}")
    List<MilestoneDefinition> findFirstTimeMilestones();

    /**
     * Busca hitos disponibles para progreso basado en conteos.
     */
    @Query("{conditionType: ?0, conditionValue: {$lte: ?1}}")
    List<MilestoneDefinition> findEligibleByCount(String conditionType, Integer currentCount);
}
