package emc.mapIt.repository;

import emc.mapIt.entity.PublicationEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PublicationEnrollmentRepository extends JpaRepository<PublicationEnrollment, Long> {

    long countByPublicationId(Long publicationId);

    boolean existsByPublicationIdAndUserId(Long publicationId, UUID userId);

    List<PublicationEnrollment> findByPublicationId(Long publicationId);

    Optional<PublicationEnrollment> findByPublicationIdAndUserId(Long publicationId, UUID userId);
}