package emc.mapIt.repository;

import emc.mapIt.entity.PublicationAccessRequest;
import emc.mapIt.entity.PublicationAccessRequestStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PublicationAccessRequestRepository extends MongoRepository<PublicationAccessRequest, String> {

    List<PublicationAccessRequest> findByPublicationId(String publicationId);

    List<PublicationAccessRequest> findByPublicationIdAndStatus(String publicationId,
            PublicationAccessRequestStatus status);

    boolean existsByPublicationIdAndRequestedByUserIdAndStatus(String publicationId, String requestedByUserId,
            PublicationAccessRequestStatus status);
}
