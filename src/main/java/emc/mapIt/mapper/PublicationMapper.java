package emc.mapIt.mapper;

import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.entity.Place;
import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationType;
import emc.mapIt.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapper de ida y vuelta para publicaciones.
 */
@Component
public class PublicationMapper {

    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    /**
     * Construye una entidad Publication a partir del request y de las relaciones ya
     * resueltas.
     *
     * @param request datos de entrada
     * @param author  entidad autor persistida
     * @param place   lugar asociado, si aplica
     * @return entidad lista para persistir
     */
    public Publication toEntity(CreatePublicationRequest request, User author, Place place) {
        Publication publication = new Publication();
        publication.setAuthor(author);
        publication.setPublicationType(PublicationType.EVENT);
        publication.setPlace(place);
        publication.setLocationTypeId(place != null ? place.getLocationTypeId() : request.locationTypeId());
        publication.setTitle(request.title().trim());
        publication.setDescription(normalizeText(request.description()));
        publication.setStartDate(toZonedDateTime(request.startDate()));
        publication.setEndDate(request.endDate() != null ? toZonedDateTime(request.endDate()) : null);
        publication.setLat(request.lat());
        publication.setLng(request.lng());
        publication.setRequiredLevel(request.requiredLevel() != null ? request.requiredLevel() : 0);
        publication.setMetadata(request.metadata() != null ? new LinkedHashMap<>(request.metadata()) : Map.of());
        publication.setActive(true);
        return publication;
    }

    /**
     * Convierte una entidad persistida a una vista serializable.
     *
     * @param publication entidad persistida
     * @return DTO de respuesta
     */
    public PublicationResponse toResponse(Publication publication, long occupiedSlots) {
        if (publication == null) {
            return null;
        }

        return new PublicationResponse(
                publication.getId(),
                publication.getAuthor() != null ? publication.getAuthor().getId() : null,
                publication.getPublicationType(),
                publication.getPlace() != null ? publication.getPlace().getId() : null,
                publication.getLocationTypeId(),
                publication.getTitle(),
                publication.getDescription(),
                publication.getStartDate(),
                publication.getEndDate(),
                publication.getLat(),
                publication.getLng(),
                publication.getRequiredLevel(),
                publication.getMetadata(),
                occupiedSlots,
                publication.getActive());
    }

    private ZonedDateTime toZonedDateTime(java.time.LocalDateTime value) {
        return value.atZone(MADRID_ZONE);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }
}
