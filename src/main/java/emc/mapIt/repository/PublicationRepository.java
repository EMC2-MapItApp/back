package emc.mapIt.repository;

import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationType;
import emc.mapIt.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {
    
    /**
     * Busca publicaciones por autor.
     */
    List<Publication> findByAuthor(User author);
    
    /**
     * Busca publicaciones activas por autor.
     */
    List<Publication> findByAuthorAndActiveTrue(User author);
    
    /**
     * Busca publicaciones por tipo.
     */
    List<Publication> findByPublicationType(PublicationType publicationType);
    
    /**
     * Busca publicaciones por tipo de ubicación.
     */
    List<Publication> findByLocationTypeId(String locationTypeId);
    
    /**
     * Busca publicaciones por lugar específico.
     */
    @Query("SELECT p FROM Publication p WHERE p.place.id = :placeId")
    List<Publication> findByPlaceId(@Param("placeId") Long placeId);
    
    /**
     * Cuenta publicaciones activas por autor (para validar límites).
     */
    @Query("SELECT COUNT(p) FROM Publication p WHERE p.author = :author AND p.active = true")
    int countActiveByAuthor(@Param("author") User author);
    
    /**
     * Cuenta publicaciones creadas esta semana por autor.
     */
    @Query("""
        SELECT COUNT(p) FROM Publication p 
        WHERE p.author = :author 
        AND p.startDate >= :weekStart
        """)
    int countByAuthorSinceDate(@Param("author") User author, @Param("weekStart") ZonedDateTime weekStart);
    
    /**
     * Busca publicaciones activas que cumplan nivel requerido.
     */
    @Query("""
        SELECT p FROM Publication p 
        WHERE p.active = true 
        AND p.requiredLevel <= :userLevel 
        AND (p.endDate IS NULL OR p.endDate > CURRENT_TIMESTAMP)
        """)
    List<Publication> findActiveForUserLevel(@Param("userLevel") Integer userLevel);
    
    /**
     * Busca publicaciones dentro de un radio específico.
     */
    @Query("""
        SELECT p FROM Publication p 
        WHERE p.active = true
        AND p.lat IS NOT NULL AND p.lng IS NOT NULL
        AND (6371 * acos(
            cos(radians(:lat)) * cos(radians(p.lat)) * 
            cos(radians(p.lng) - radians(:lng)) + 
            sin(radians(:lat)) * sin(radians(p.lat))
        )) <= :radiusKm
        """)
    List<Publication> findActiveWithinRadius(@Param("lat") BigDecimal lat, 
                                           @Param("lng") BigDecimal lng, 
                                           @Param("radiusKm") Double radiusKm);
    
    /**
     * Busca publicaciones por múltiples criterios.
     */
    @Query("""
        SELECT p FROM Publication p 
        WHERE p.active = true
        AND (:publicationType IS NULL OR p.publicationType = :publicationType)
        AND (:locationTypeId IS NULL OR p.locationTypeId = :locationTypeId)
        AND (:authorId IS NULL OR p.author.id = :authorId)
        AND p.requiredLevel <= :maxLevel
        ORDER BY p.startDate DESC
        """)
    List<Publication> findByCriteria(@Param("publicationType") PublicationType publicationType,
                                   @Param("locationTypeId") String locationTypeId,
                                   @Param("authorId") UUID authorId,
                                   @Param("maxLevel") Integer maxLevel);
}
