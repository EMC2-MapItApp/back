package emc.mapIt.service;

import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.Publication;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.mapper.PublicationMapper;
import emc.mapIt.repository.LocationTypeRepository;
import emc.mapIt.repository.PublicationRepository;
import emc.mapIt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de aplicación para publicaciones persistidas.
 * <p>
 * Se encarga de validar el alta de actividades, persistirlas y exponer vistas
 * serializables para el frontend.
 * </p>
 */
@Service
@Transactional
public class PublicationService {

    private static final Logger log = LoggerFactory.getLogger(PublicationService.class);
    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    private final PublicationRepository publicationRepository;
    private final UserRepository userRepository;
    private final LocationTypeRepository locationTypeRepository;
    private final PublicationMapper publicationMapper;

    /**
     * Constructor para inyección de dependencias.
     */
    public PublicationService(PublicationRepository publicationRepository,
            UserRepository userRepository,
            LocationTypeRepository locationTypeRepository,
            PublicationMapper publicationMapper) {
        this.publicationRepository = publicationRepository;
        this.userRepository = userRepository;
        this.locationTypeRepository = locationTypeRepository;
        this.publicationMapper = publicationMapper;
    }

    /**
     * Crea una actividad/evento para un usuario particular.
     * <p>
     * Reglas aplicadas:
     * </p>
     * <ul>
     * <li>El autor debe existir.</li>
     * <li>El autor debe ser de tipo PARTICULAR o ADMIN.</li>
     * <li>El evento no puede venir asociado a un Place.</li>
     * <li>El locationTypeId debe existir.</li>
     * <li>Latitud y longitud son obligatorias.</li>
     * </ul>
     *
     * @param authorId id del usuario autenticado
     * @param request  payload de creación
     * @return publicación persistida
     */
    public PublicationResponse createEvent(UUID authorId, CreatePublicationRequest request) {
        if (authorId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }
        if (request == null) {
            throw new ApiException("BAD_REQUEST", "Request de publicación requerida", HttpStatus.BAD_REQUEST);
        }

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        if (author.getUserType() != UserType.PARTICULAR && author.getUserType() != UserType.ADMIN) {
            throw new ApiException("FORBIDDEN",
                    "Solo los usuarios particulares o administradores pueden crear esta actividad",
                    HttpStatus.FORBIDDEN);
        }

        if (request.placeId() != null) {
            throw new ApiException("BAD_REQUEST", "Los eventos de particulares no pueden estar asociados a un lugar",
                    HttpStatus.BAD_REQUEST);
        }

        LocationType locationType = locationTypeRepository.findById(request.locationTypeId())
                .orElseThrow(
                        () -> new ApiException("NOT_FOUND", "Tipo de ubicación no encontrado", HttpStatus.NOT_FOUND));

        if (request.lat() == null || request.lng() == null) {
            throw new ApiException("BAD_REQUEST", "Latitud y longitud son obligatorias", HttpStatus.BAD_REQUEST);
        }

        if (request.title() == null || request.title().trim().isEmpty()) {
            throw new ApiException("BAD_REQUEST", "El título es obligatorio", HttpStatus.BAD_REQUEST);
        }

        if (request.startDate() == null) {
            throw new ApiException("BAD_REQUEST", "La fecha de inicio es obligatoria", HttpStatus.BAD_REQUEST);
        }

        log.debug("LocationType resuelto para alta publication id={} name={}", locationType.getId(),
                locationType.getName());

        Publication publication = publicationMapper.toEntity(request, author, null);

        // Para actividades, si no se informa fecha de fin se asume fin de día local.
        if (publication.getEndDate() == null) {
            publication.setEndDate(publication.getStartDate()
                    .withHour(23)
                    .withMinute(59)
                    .withSecond(59)
                    .withNano(0));
        }

        if (publication.getEndDate().isBefore(publication.getStartDate())) {
            throw new ApiException("BAD_REQUEST",
                    "La fecha de fin no puede ser anterior a la fecha de inicio",
                    HttpStatus.BAD_REQUEST);
        }

        publication.setLocationTypeId(locationType.getId());
        Publication saved = publicationRepository.save(publication);

        log.info("Actividad creada publicationId={} authorId={}", saved.getId(), authorId);
        return publicationMapper.toResponse(saved);
    }

    /**
     * Recupera publicaciones por autor.
     *
     * @param authorId   id del usuario
     * @param activeOnly si true, filtra solo activas
     * @return lista de publicaciones serializables
     */
    @Transactional(readOnly = true)
    public List<PublicationResponse> findByAuthor(UUID authorId, boolean activeOnly) {
        expireFinishedPublications();

        if (authorId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        List<Publication> publications = activeOnly
                ? publicationRepository.findByAuthorAndActiveTrue(author)
                : publicationRepository.findByAuthor(author);

        return publications.stream()
                .map(publicationMapper::toResponse)
                .toList();
    }

    /**
     * Recupera publicaciones para el mapa.
     * <p>
     * Por defecto solo devuelve publicaciones activas para no mostrar contenido
     * caducado o deshabilitado en la vista pública.
     * </p>
     *
     * @param activeOnly si true, devuelve solo publicaciones activas
     * @return lista de publicaciones serializables
     */
    @Transactional(readOnly = true)
    public List<PublicationResponse> findAll(boolean activeOnly) {
        expireFinishedPublications();

        List<Publication> publications = activeOnly
                ? publicationRepository.findByActiveTrueOrderByStartDateDesc()
                : publicationRepository.findAll();

        return publications.stream()
                .map(publicationMapper::toResponse)
                .toList();
    }

    /**
     * Recupera una publicación por id.
     *
     * @param id identificador de la publicación
     * @return respuesta serializable
     */
    @Transactional(readOnly = true)
    public PublicationResponse findById(Long id) {
        expireFinishedPublications();

        if (id == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        Publication publication = publicationRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));
        return publicationMapper.toResponse(publication);
    }

    /**
     * Elimina definitivamente una publicación.
     *
     * @param publicationId id de la publicación a eliminar
     * @param requesterId   id del usuario autenticado que solicita el borrado
     */
    public void deleteById(Long publicationId, UUID requesterId) {
        if (publicationId == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        if (requesterId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        boolean isAuthor = publication.getAuthor() != null && requesterId.equals(publication.getAuthor().getId());
        boolean isAdmin = requester.getUserType() == UserType.ADMIN;
        if (!isAuthor && !isAdmin) {
            throw new ApiException("FORBIDDEN",
                    "No tienes permisos para eliminar esta publicación",
                    HttpStatus.FORBIDDEN);
        }

        publicationRepository.delete(publication);
        log.info("Publicación eliminada publicationId={} requesterId={}", publicationId, requesterId);
    }

    /**
     * Marca como finalizadas las actividades activas con fecha de fin vencida.
     */
    private void expireFinishedPublications() {
        ZonedDateTime now = ZonedDateTime.now(MADRID_ZONE);
        List<Publication> expired = publicationRepository.findByActiveTrueAndEndDateBefore(now);
        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(publication -> publication.setActive(false));
        publicationRepository.saveAll(expired);
        log.debug("Se han marcado {} publicaciones como finalizadas", expired.size());
    }
}
