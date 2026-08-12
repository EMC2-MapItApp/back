package emc.mapIt.dto;

import emc.mapIt.entity.PublicationType;
import emc.mapIt.entity.PublicationVisibility;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Vista serializable de una publicación persistida.
 * <p>
 * {@code groupName}/{@code groupMemberCount}/{@code isGroupMember}/{@code accessRequestPending}
 * son {@code null} en publicaciones {@code PUBLIC}. {@code occupiedSlots} es {@code null}
 * exactamente cuando {@code visibility == PRIVATE_GROUP && !isGroupMember} — es el único punto de
 * fuga de aforo real hacia quien no es miembro del grupo (ver {@code PublicationMapper#toResponse}).
 * </p>
 */
public record PublicationResponse(
                String id,
                String authorId,
                PublicationType publicationType,
                String placeId,
                String locationTypeId,
                String title,
                String description,
                ZonedDateTime startDate,
                ZonedDateTime endDate,
                BigDecimal lat,
                BigDecimal lng,
                Integer requiredLevel,
                Map<String, Object> metadata,
                Long occupiedSlots,
                Boolean active,
                PublicationVisibility visibility,
                String groupId,
                String groupName,
                Integer groupMemberCount,
                Boolean isGroupMember,
                Boolean accessRequestPending) {
}
