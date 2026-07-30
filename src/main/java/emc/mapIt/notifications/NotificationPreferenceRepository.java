package emc.mapIt.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Acceso a {@link NotificationPreference} — preferencias de email por tipo, una por usuario. */
@Repository
public interface NotificationPreferenceRepository extends MongoRepository<NotificationPreference, String> {

    Optional<NotificationPreference> findByUserId(String userId);
}
