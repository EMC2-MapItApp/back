package emc.mapIt.repository;

import emc.mapIt.entity.CapabilityDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CapabilityDefinitionRepository extends JpaRepository<CapabilityDefinition, String> {
    
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
    @Query("SELECT cd FROM CapabilityDefinition cd WHERE cd.unlocksAtLevel IS NULL OR cd.unlocksAtLevel <= :maxLevel")
    List<CapabilityDefinition> findAvailableForLevel(@Param("maxLevel") Integer maxLevel);
    
    /**
     * Busca capacidades por tipo (basándose en prefijo del ID).
     */
    @Query("SELECT cd FROM CapabilityDefinition cd WHERE cd.id LIKE CONCAT(:prefix, '%')")
    List<CapabilityDefinition> findByIdPrefix(@Param("prefix") String prefix);
}
