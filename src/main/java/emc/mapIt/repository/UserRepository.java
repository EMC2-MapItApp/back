package emc.mapIt.repository;

import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca un usuario por email (para autenticación).
     */
    Optional<User> findByEmail(String email);

    /**
     * Verifica si existe un usuario con el email dado.
     */
    boolean existsByEmail(String email);

    /**
     * Busca usuarios por tipo.
     */
    List<User> findByUserType(UserType userType);

    /**
     * Busca usuarios por nivel de gamificación.
     */
    List<User> findByProfileDetailsLevel(Integer level);

    /**
     * Busca usuarios por rango de XP.
     */
    @Query("SELECT u FROM User u JOIN u.profileDetails p WHERE p.xp BETWEEN :minXp AND :maxXp")
    List<User> findByXpRange(@Param("minXp") Integer minXp, @Param("maxXp") Integer maxXp);

    /**
     * Busca usuarios que tienen una capacidad específica desbloqueada.
     * Usa jsonb_exists para PostgreSQL.
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE jsonb_exists(u.unlocked_capabilities, ?1)
            """, nativeQuery = true)
    List<User> findByUnlockedCapability(String capability);

    /**
     * Busca usuarios por tipo de ubicación favorita.
     * Usa jsonb_exists para PostgreSQL.
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE jsonb_exists(u.favorite_location_type_ids, ?1)
            """, nativeQuery = true)
    List<User> findByFavoriteLocationType(Long locationTypeId);

    /**
     * Busca usuarios por nombre (búsqueda parcial).
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<User> findByNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Busca usuarios con XP mayor a un valor específico.
     */
    @Query("SELECT u FROM User u JOIN u.profileDetails p WHERE p.xp > :minXp ORDER BY p.xp DESC")
    List<User> findByXpGreaterThan(@Param("minXp") Integer minXp);

    /**
     * Cuenta usuarios por tipo.
     */
    long countByUserType(UserType userType);

    /**
     * Alternativa: Busca usuarios que contienen una capacidad usando LIKE.
     * (En caso de que jsonb_exists dé problemas)
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE u.unlocked_capabilities::text LIKE CONCAT('%"', ?1, '"%')
            """, nativeQuery = true)
    List<User> findByUnlockedCapabilityAlternative(String capability);

    /**
     * Alternativa: Busca usuarios por tipo de ubicación usando LIKE.
     * (En caso de que jsonb_exists dé problemas)
     */
    @Query(value = """
            SELECT * FROM users u
            WHERE u.favorite_location_type_ids::text LIKE CONCAT('%"', ?1, '"%')
            """, nativeQuery = true)
    List<User> findByFavoriteLocationTypeAlternative(Long locationTypeId);

    /**
     * Busca usuario por id cargando su detalle en la misma query (evita problema
     * LAZY en update).
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.profileDetails WHERE u.id = :id")
    Optional<User> findByIdWithProfile(@Param("id") UUID id);

    /**
     * Busca usuario por email cargando su detalle en la misma query.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.profileDetails WHERE u.email = :email")
    Optional<User> findByEmailWithProfile(@Param("email") String email);
}