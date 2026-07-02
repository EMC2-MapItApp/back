package emc.mapIt.dto;

import emc.mapIt.entity.PublicationType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Vista serializable de una publicación persistida.
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
                Boolean active) {
}
