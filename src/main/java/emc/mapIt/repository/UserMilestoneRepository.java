package emc.mapIt.repository;

import emc.mapIt.entity.UserMilestone;
import emc.mapIt.entity.UserMilestoneId;
import emc.mapIt.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserMilestoneRepository extends JpaRepository<UserMilestone, UserMilestoneId> {
    
    /**
     * Busca todos los hitos completados por un usuario.
     */
    List<UserMilestone> findByUserId(UUID userId);
    
    /**
     * Busca hitos completados por usuario ordenados por fecha.
     */
    List<UserMilestone> findByUserIdOrderByCompletedAtDesc(UUID userId);
    
    /**
     * Verifica si un usuario ya completó un hito específico.
     */
    boolean existsByUserIdAndMilestoneId(UUID userId, String milestoneId);
    
    /**
     * Busca hitos completados por un usuario en un periodo específico.
     */
    @Query("""
        SELECT um FROM UserMilestone um 
        WHERE um.userId = :userId 
        AND um.completedAt BETWEEN :startDate AND :endDate
        """)
    List<UserMilestone> findByUserIdAndDateRange(@Param("userId") UUID userId,
                                               @Param("startDate") ZonedDateTime startDate,
                                               @Param("endDate") ZonedDateTime endDate);
    
    /**
     * Cuenta los hitos completados por un usuario.
     */
    int countByUserId(UUID userId);
    
    /**
     * Busca hitos completados por tipo de condición.
     */
    @Query("""
        SELECT um FROM UserMilestone um 
        JOIN um.milestoneDefinition md
        WHERE um.userId = :userId 
        AND md.conditionType = :conditionType
        """)
    List<UserMilestone> findByUserIdAndConditionType(@Param("userId") UUID userId,
                                                    @Param("conditionType") String conditionType);
    
    /**
     * Calcula el XP total ganado por un usuario a través de hitos.
     */
    @Query("""
        SELECT COALESCE(SUM(md.xpReward), 0) 
        FROM UserMilestone um 
        JOIN um.milestoneDefinition md
        WHERE um.userId = :userId
        """)
    Integer calculateTotalXpFromMilestones(@Param("userId") UUID userId);
    
    /**
     * Busca los últimos hitos completados por un usuario (para notificaciones).
     * Usando consulta nativa para LIMIT en PostgreSQL.
     */
    @Query(value = """
        SELECT * FROM user_milestones um 
        WHERE um.user_id = :userId 
        ORDER BY um.completed_at DESC 
        LIMIT :limit
        """, nativeQuery = true)
    List<UserMilestone> findRecentByUserId(@Param("userId") UUID userId, @Param("limit") int limit);
    
    /**
     * Busca usuarios que completaron un hito específico.
     */
    @Query("SELECT um.userId FROM UserMilestone um WHERE um.milestoneId = :milestoneId")
    List<UUID> findUserIdsByMilestoneId(@Param("milestoneId") String milestoneId);
    
    /**
     * Busca estadísticas de hitos completados por mes.
     * Usando funciones de fecha de PostgreSQL.
     */
    @Query(value = """
        SELECT EXTRACT(YEAR FROM um.completed_at) as year, 
               EXTRACT(MONTH FROM um.completed_at) as month, 
               COUNT(*) as count
        FROM user_milestones um 
        WHERE um.user_id = :userId 
        GROUP BY EXTRACT(YEAR FROM um.completed_at), EXTRACT(MONTH FROM um.completed_at)
        ORDER BY year DESC, month DESC
        """, nativeQuery = true)
    List<Object[]> findMilestoneStatsByMonth(@Param("userId") UUID userId);
}