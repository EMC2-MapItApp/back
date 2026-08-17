package emc.mapIt.repository;

import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@link User}. Además de las consultas usadas hoy por {@code AuthService}/
 * {@code UserService} ({@code findByEmail}, {@code existsByEmail}, {@code existsByNick}), incluye
 * derivadas aún sin consumidor pensadas para funcionalidad en progreso (búsqueda por nick,
 * segmentación por tipo de usuario o por nivel/XP para un futuro ranking).
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByNick(String nick);

    boolean existsByNick(String nick);

    List<User> findByUserType(UserType userType);

    List<User> findByProfileDetailsLevel(Integer level);

    List<User> findByUnlockedCapabilities(String capability);

    List<User> findByFavoriteLocationTypeIds(String locationTypeId);

    List<User> findByNameContainingIgnoreCase(String name);

    /**
     * Usado por el buscador de invitación a grupos: coincidencia parcial por nick o email.
     * Recibe {@link Pageable} para que el límite de resultados se aplique en la propia query de
     * Mongo, no sobre el stream completo ya traído a memoria.
     */
    List<User> findByNickContainingIgnoreCaseOrEmailContainingIgnoreCase(String nick, String email, Pageable pageable);

    List<User> findByProfileDetailsXpGreaterThanOrderByProfileDetailsXpDesc(Integer minXp);

    long countByUserType(UserType userType);

    Optional<User> findById(String id);
}