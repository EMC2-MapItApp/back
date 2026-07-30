package emc.mapIt.notifications;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Acceso a {@link Notification} — historial del centro de notificaciones por usuario. */
@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndReadFalse(String userId);

    /** Busca por id y dueño a la vez — así "marcar como leída" no puede tocar notificaciones ajenas. */
    Optional<Notification> findByIdAndUserId(String id, String userId);

    List<Notification> findByUserIdAndReadFalse(String userId);

    void deleteByUserId(String userId);
}
