package emc.mapIt.mapper;

import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.entity.Place;
import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationType;
import emc.mapIt.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mapper de ida y vuelta para publicaciones.
 */
@Component
public class PublicationMapper {

    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    /** Radio de la zona de difuminado para ubicaciones no exactas. */
    private static final double APPROXIMATE_RADIUS_KM = 5.0;

    /** Kilómetros por grado de latitud (aproximación esférica, válida para radios pequeños). */
    private static final double KM_PER_DEGREE_LAT = 111.32;

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
        publication.setAuthorId(author != null ? author.getId() : null);
        publication.setPublicationType(PublicationType.EVENT);
        publication.setPlaceId(place != null ? place.getId() : null);
        publication.setLocationTypeId(place != null ? place.getLocationTypeId() : request.locationTypeId());
        publication.setTitle(request.title().trim());
        publication.setDescription(normalizeText(request.description()));
        publication.setStartDate(toZonedDateTime(request.startDate()));
        publication.setEndDate(request.endDate() != null ? toZonedDateTime(request.endDate()) : null);
        publication.setRequiredLevel(request.requiredLevel() != null ? request.requiredLevel() : 0);
        publication.setMetadata(request.metadata() != null ? new LinkedHashMap<>(request.metadata()) : Map.of());
        publication.setActive(true);

        if (request.lat() != null && request.lng() != null && !isExactLocation(publication.getMetadata())) {
            BigDecimal[] approximate = randomPointWithinRadius(request.lat(), request.lng(), APPROXIMATE_RADIUS_KM);
            publication.setLat(approximate[0]);
            publication.setLng(approximate[1]);
        } else {
            publication.setLat(request.lat());
            publication.setLng(request.lng());
        }

        return publication;
    }

    /**
     * Lee {@code metadata.exactLocation}; ausente o con un valor no booleano se trata como
     * ubicación exacta (comportamiento por defecto/legacy).
     */
    private boolean isExactLocation(Map<String, Object> metadata) {
        Object value = metadata != null ? metadata.get("exactLocation") : null;
        return !(value instanceof Boolean exact) || exact;
    }

    /**
     * Calcula un punto aleatorio dentro de un disco de radio {@code radiusKm} centrado en
     * {@code (lat, lng)}, con distribución uniforme en superficie (evita concentrar puntos en el
     * centro) y corrección de longitud por {@code cos(lat)}.
     */
    private BigDecimal[] randomPointWithinRadius(BigDecimal lat, BigDecimal lng, double radiusKm) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double distanceKm = radiusKm * Math.sqrt(random.nextDouble());
        double bearingRad = random.nextDouble() * 2 * Math.PI;

        double latRad = Math.toRadians(lat.doubleValue());
        double deltaLat = (distanceKm * Math.cos(bearingRad)) / KM_PER_DEGREE_LAT;
        double deltaLng = (distanceKm * Math.sin(bearingRad)) / (KM_PER_DEGREE_LAT * Math.cos(latRad));

        BigDecimal newLat = BigDecimal.valueOf(lat.doubleValue() + deltaLat).setScale(6, RoundingMode.HALF_UP);
        BigDecimal newLng = BigDecimal.valueOf(lng.doubleValue() + deltaLng).setScale(6, RoundingMode.HALF_UP);
        return new BigDecimal[] { newLat, newLng };
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
                publication.getAuthorId(),
                publication.getPublicationType(),
                publication.getPlaceId(),
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
