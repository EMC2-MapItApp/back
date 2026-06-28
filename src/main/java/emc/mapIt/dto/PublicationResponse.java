package emc.mapIt.dto;

import emc.mapIt.entity.PublicationType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Vista serializable de una publicación persistida.
 * <p>
 * Se devuelve tanto al crear una actividad como al listar publicaciones por
 * autor.
 * </p>
 */
public record PublicationResponse(
                /** Identificador único de la publicación. */
                Long id,

                /** Identificador del autor. */
                UUID authorId,

                /** Tipo de publicación persistida. */
                PublicationType publicationType,

                /** Id del lugar asociado, si existe. */
                Long placeId,

                /** Id del tipo de ubicación. */
                Long locationTypeId,

                /** Título visible. */
                String title,

                /** Descripción opcional. */
                String description,

                /** Fecha y hora de inicio con zona. */
                ZonedDateTime startDate,

                /** Fecha y hora de fin con zona. */
                ZonedDateTime endDate,

                /** Latitud persistida. */
                BigDecimal lat,

                /** Longitud persistida. */
                BigDecimal lng,

                /** Nivel mínimo requerido. */
                Integer requiredLevel,

                /** Metadatos JSONB. */
                Map<String, Object> metadata,

                /** Número actual de inscritos/apuntados. */
                Long occupiedSlots,

                /** Estado activo de la publicación. */
                Boolean active) {
}
