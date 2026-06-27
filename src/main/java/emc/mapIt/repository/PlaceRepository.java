package emc.mapIt.repository;

import emc.mapIt.entity.Place;
import emc.mapIt.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {
    
    /**
     * Busca lugares por propietario.
     */
    List<Place> findByOwner(User owner);
    
    /**
     * Busca el lugar de un usuario específico (máximo 1 para professional/entity).
     */
    Optional<Place> findByOwnerId(UUID ownerId);
    
    /**
     * Cuenta lugares por propietario (para validar límites).
     */
    int countByOwner(User owner);
    
    /**
     * Busca lugares por tipo de ubicación.
     */
    List<Place> findByLocationTypeId(String locationTypeId);
    
    /**
     * Busca lugares dentro de un radio específico.
     * Calcula distancia usando fórmula Haversine.
     */
    @Query("""
        SELECT p FROM Place p 
        WHERE (6371 * acos(
            cos(radians(:lat)) * cos(radians(p.lat)) * 
            cos(radians(p.lng) - radians(:lng)) + 
            sin(radians(:lat)) * sin(radians(p.lat))
        )) <= :radiusKm
        """)
    List<Place> findWithinRadius(@Param("lat") BigDecimal lat, 
                                @Param("lng") BigDecimal lng, 
                                @Param("radiusKm") Double radiusKm);
    
    /**
     * Busca lugares por tipo de ubicación y radio.
     */
    @Query("""
        SELECT p FROM Place p 
        WHERE p.locationTypeId = :locationTypeId
        AND (6371 * acos(
            cos(radians(:lat)) * cos(radians(p.lat)) * 
            cos(radians(p.lng) - radians(:lng)) + 
            sin(radians(:lat)) * sin(radians(p.lat))
        )) <= :radiusKm
        """)
    List<Place> findByLocationTypeAndRadius(@Param("locationTypeId") String locationTypeId,
                                          @Param("lat") BigDecimal lat, 
                                          @Param("lng") BigDecimal lng, 
                                          @Param("radiusKm") Double radiusKm);
    
    /**
     * Busca lugares que contengan texto en el nombre o descripción.
     */
    @Query("SELECT p FROM Place p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Place> findByNameOrDescriptionContaining(@Param("searchTerm") String searchTerm);
}
