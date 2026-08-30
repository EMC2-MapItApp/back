package emc.mapIt.dto;

import emc.mapIt.entity.PublicationType;
import emc.mapIt.entity.PublicationVisibility;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Vista serializable de una publicación persistida.
 * <p>
 * {@code hasAccess} es siempre {@code true} en publicaciones {@code PUBLIC}; en {@code PRIVATE}
 * refleja si el viewer es el autor, un ADMIN, o tiene una {@code PublicationInvitation} en estado
 * distinto de {@code DECLINED}. Cuando {@code visibility == PRIVATE && !hasAccess}, {@code title},
 * {@code description}, {@code metadata} y {@code occupiedSlots} llegan a {@code null} — es el
 * enmascarado que evita filtrar el contenido de una publicación privada a quien no tiene acceso
 * (ver {@code PublicationMapper#toResponse}). {@code lat}/{@code lng} se mantienen siempre para que
 * el pin pueda dibujarse en el mapa. {@code accessRequestPending} es {@code null} en publicaciones
 * {@code PUBLIC}.
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
                Boolean hasAccess,
                Boolean accessRequestPending) {
}
