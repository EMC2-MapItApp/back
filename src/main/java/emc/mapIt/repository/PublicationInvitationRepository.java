package emc.mapIt.repository;

import emc.mapIt.entity.PublicationInvitation;
import emc.mapIt.entity.PublicationInvitationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PublicationInvitationRepository extends MongoRepository<PublicationInvitation, String> {

    List<PublicationInvitation> findByPublicationId(String publicationId);

    List<PublicationInvitation> findByInvitedUserId(String invitedUserId);

    boolean existsByPublicationIdAndInvitedUserIdAndStatusNot(String publicationId, String invitedUserId,
            PublicationInvitationStatus status);
}
