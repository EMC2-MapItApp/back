package emc.mapIt.repository;

import emc.mapIt.entity.CapabilityDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CapabilityDefinitionRepository extends MongoRepository<CapabilityDefinition, String> {

    /**
     * Busca capacidades que se desbloquean en un nivel específico.
     */
    List<CapabilityDefinition> findByUnlocksAtLevel(Integer level);

    /**
     * Busca capacidades que se pueden comprar.
     */
    List<CapabilityDefinition> findByPurchasableTrue();

    /**
     * Busca capacidades desbloqueables hasta un nivel específico.
     */
    @Query("{$or: [{unlocksAtLevel: null}, {unlocksAtLevel: {$lte: ?0}}]}")
    List<CapabilityDefinition> findAvailableForLevel(Integer maxLevel);

    /**
     * Busca capacidades por tipo (basándose en prefijo del ID).
     */
    @Query("{_id: {$regex: '^?0'}}")
    List<CapabilityDefinition> findByIdPrefix(String prefix);
}
