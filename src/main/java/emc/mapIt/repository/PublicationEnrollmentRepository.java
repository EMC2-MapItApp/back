package emc.mapIt.repository;

import emc.mapIt.entity.PublicationEnrollment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Acceso a {@link PublicationEnrollment} — inscripciones de usuarios a publicaciones. */
@Repository
public interface PublicationEnrollmentRepository extends MongoRepository<PublicationEnrollment, String> {

    long countByPublicationId(String publicationId);

    boolean existsByPublicationIdAndUserId(String publicationId, String userId);

    List<PublicationEnrollment> findByPublicationId(String publicationId);

    Optional<PublicationEnrollment> findByPublicationIdAndUserId(String publicationId, String userId);
}