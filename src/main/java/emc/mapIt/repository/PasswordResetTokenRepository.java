package emc.mapIt.repository;

import emc.mapIt.entity.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Acceso a {@link PasswordResetToken} — el token en sí nunca se persiste en claro, solo su hash. */
@Repository
public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(String userId);

    void deleteByUserIdAndConsumedAtIsNull(String userId);
}
