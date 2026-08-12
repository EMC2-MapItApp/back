package emc.mapIt.groups;

import java.time.Instant;

/**
 * Vista serializable de una {@link GroupJoinRequest}. No incluye el título de la publicación que
 * la originó — resolverlo requeriría que este módulo (grupos) llamara al módulo de publicaciones,
 * invirtiendo la dirección de dependencia (publicaciones → grupos) que fija esta misma feature.
 */
public record GroupJoinRequestResponse(
        String id,
        String groupId,
        String groupName,
        String requestedByUserId,
        String requestedByName,
        String requestedByNick,
        String publicationId,
        GroupJoinRequestStatus status,
        Instant createdAt,
        Instant respondedAt) {
}
