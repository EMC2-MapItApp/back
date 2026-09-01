package emc.mapIt.repository;

import emc.mapIt.entity.CapabilityDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.regex.Pattern;

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
     * <p>
     * Sin caller hoy (código muerto), pero de tenerlo, un {@code prefix} con metacaracteres de
     * regex sin escapar (p. ej. {@code .*}) alteraría el filtro más allá de un simple "empieza
     * por". Se delega en un método por defecto que escapa antes de llamar a la query derivada.
     */
    @Query("{_id: {$regex: '^?0'}}")
    List<CapabilityDefinition> findByIdRegexPrefix(String escapedPrefix);

    default List<CapabilityDefinition> findByIdPrefix(String prefix) {
        // \Q...\E es válido tanto en regex de Java como en PCRE (motor de $regex de Mongo):
        // trata el contenido como literal, sin interpretar metacaracteres.
        return findByIdRegexPrefix(Pattern.quote(prefix));
    }
}
