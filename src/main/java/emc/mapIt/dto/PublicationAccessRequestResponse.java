package emc.mapIt.dto;

import emc.mapIt.entity.PublicationAccessRequestStatus;

import java.time.Instant;

/** Vista serializable de una {@link emc.mapIt.entity.PublicationAccessRequest}. */
public record PublicationAccessRequestResponse(
        String id,
        String publicationId,
        String publicationTitle,
        String requestedByUserId,
        String requestedByName,
        String requestedByNick,
        PublicationAccessRequestStatus status,
        Instant createdAt,
        Instant respondedAt) {
}
