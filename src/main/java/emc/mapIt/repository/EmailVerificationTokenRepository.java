package emc.mapIt.repository;

import emc.mapIt.entity.EmailVerificationToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Acceso a {@link EmailVerificationToken} — el token en sí nunca se persiste en claro, solo su hash. */
@Repository
public interface EmailVerificationTokenRepository extends MongoRepository<EmailVerificationToken, String> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(String userId);

    void deleteByUserIdAndConsumedAtIsNull(String userId);
}
