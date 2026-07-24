package emc.mapIt.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Acceso a {@link PushSubscription} — dispositivos suscritos a push por usuario. */
@Repository
public interface PushSubscriptionRepository extends MongoRepository<PushSubscription, String> {

    List<PushSubscription> findByUserId(String userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);
}
