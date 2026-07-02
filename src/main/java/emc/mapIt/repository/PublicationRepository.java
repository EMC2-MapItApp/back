package emc.mapIt.repository;

import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface PublicationRepository extends MongoRepository<Publication, String> {

    /**
     * Busca publicaciones por autor.
     */
    List<Publication> findByAuthorId(String authorId);

    /**
     * Busca publicaciones activas por autor.
     */
    List<Publication> findByAuthorIdAndActiveTrue(String authorId);

    /**
     * Busca publicaciones activas ordenadas por fecha de inicio descendente.
     */
    List<Publication> findByActiveTrueOrderByStartDateDesc();

    /**
     * Busca publicaciones activas cuya fecha de fin ya ha pasado.
     */
    List<Publication> findByActiveTrueAndEndDateBefore(ZonedDateTime dateTime);

    /**
     * Busca publicaciones por tipo.
     */
    List<Publication> findByPublicationType(PublicationType publicationType);

    /**
     * Busca publicaciones por tipo de ubicación.
     */
    List<Publication> findByLocationTypeId(String locationTypeId);

    /**
     * Busca publicaciones por lugar específico.
     */
    List<Publication> findByPlaceId(String placeId);

    /**
     * Cuenta publicaciones activas por autor (para validar límites).
     */
    int countByAuthorIdAndActiveTrue(String authorId);

    /**
     * Cuenta publicaciones creadas por autor desde una fecha.
     */
    int countByAuthorIdAndStartDateGreaterThanEqual(String authorId, ZonedDateTime weekStart);

    /**
     * Busca publicaciones activas que cumplan nivel requerido y no hayan expirado.
     */
    @Query("{active: true, requiredLevel: {$lte: ?0}, $or: [{endDate: null}, {endDate: {$gt: '$$NOW'}}]}")
    List<Publication> findActiveForUserLevel(Integer userLevel);

    /**
     * Busca publicaciones activas con coordenadas por tipo y nivel.
     * El filtro de radio debe aplicarse en el servicio con $geoNear o cálculo en memoria.
     */
    @Query("{active: true, lat: {$exists: true}, lng: {$exists: true}, publicationType: ?0, requiredLevel: {$lte: ?1}}")
    List<Publication> findActiveWithCoordinatesByTypeAndLevel(String publicationType, Integer maxLevel);
}
