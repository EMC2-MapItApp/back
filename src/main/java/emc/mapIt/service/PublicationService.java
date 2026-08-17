package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.ChangeVisibilityRequest;
import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.EnrollmentDto;
import emc.mapIt.dto.PublicationAccessRequestResponse;
import emc.mapIt.dto.PublicationEnrollmentResponse;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationAccessRequest;
import emc.mapIt.entity.PublicationAccessRequestStatus;
import emc.mapIt.entity.PublicationEnrollment;
import emc.mapIt.entity.PublicationInvitation;
import emc.mapIt.entity.PublicationInvitationStatus;
import emc.mapIt.entity.PublicationVisibility;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.mapper.PublicationMapper;
import emc.mapIt.notifications.NotificationService;
import emc.mapIt.repository.PublicationAccessRequestRepository;
import emc.mapIt.repository.PublicationEnrollmentRepository;
import emc.mapIt.repository.LocationTypeRepository;
import emc.mapIt.repository.PublicationInvitationRepository;
import emc.mapIt.repository.PublicationRepository;
import emc.mapIt.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final int EXPIRED_RETENTION_MONTHS = 3;

    private final PublicationRepository publicationRepository;
    private final PublicationEnrollmentRepository publicationEnrollmentRepository;
    private final PublicationInvitationRepository publicationInvitationRepository;
    private final PublicationAccessRequestRepository publicationAccessRequestRepository;
    private final UserRepository userRepository;
    private final LocationTypeRepository locationTypeRepository;
    private final PublicationMapper publicationMapper;
    private final UserService userService;
    private final NotificationService notificationService;
    private final PublicationInvitationDispatcher publicationInvitationDispatcher;

    /**
     * Constructor para inyección de dependencias.
     */
    public PublicationService(PublicationRepository publicationRepository,
            PublicationEnrollmentRepository publicationEnrollmentRepository,
            PublicationInvitationRepository publicationInvitationRepository,
            PublicationAccessRequestRepository publicationAccessRequestRepository,
            UserRepository userRepository,
            LocationTypeRepository locationTypeRepository,
            PublicationMapper publicationMapper,
            UserService userService,
            NotificationService notificationService,
            PublicationInvitationDispatcher publicationInvitationDispatcher) {
        this.publicationRepository = publicationRepository;
        this.publicationEnrollmentRepository = publicationEnrollmentRepository;
        this.publicationInvitationRepository = publicationInvitationRepository;
        this.publicationAccessRequestRepository = publicationAccessRequestRepository;
        this.userRepository = userRepository;
        this.locationTypeRepository = locationTypeRepository;
        this.publicationMapper = publicationMapper;
        this.userService = userService;
        this.notificationService = notificationService;
        this.publicationInvitationDispatcher = publicationInvitationDispatcher;
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
    public PublicationResponse createEvent(String authorId, CreatePublicationRequest request) {
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

        // El autor queda apuntado por defecto a su propia publicación — puede desapuntarse
        // después como cualquier otro asistente (ver #unenroll).
        publicationEnrollmentRepository.save(
                new PublicationEnrollment(saved.getId(), authorId, ZonedDateTime.now(MADRID_ZONE)));

        // Las invitaciones se envían en segundo plano (ver PublicationInvitationDispatcher): no
        // bloquean esta respuesta, que ya puede devolver la publicación como creada. No depende
        // de la visibilidad — en PUBLIC es solo un aviso, en PRIVATE es el mecanismo de acceso
        // (ver #enroll). Invitar a los integrantes de un grupo es responsabilidad del cliente
        // (expandir el grupo a esta misma lista antes de enviar la petición).
        if (request.inviteUserIds() != null && !request.inviteUserIds().isEmpty()) {
            Set<String> invitees = new LinkedHashSet<>(request.inviteUserIds());
            invitees.remove(authorId);
            if (!invitees.isEmpty()) {
                publicationInvitationDispatcher.dispatchAsync(saved, authorId, invitees);
            }
        }

        // El autor siempre tiene acceso completo a su propia publicación, sin enmascarado.
        return publicationMapper.toResponse(saved, 1, true, null);
    }

    /**
     * Recupera publicaciones por autor.
     *
     * @param authorId   id del usuario
     * @param activeOnly si true, filtra solo activas
     * @param viewerId   id de quien consulta (para calcular acceso a publicaciones {@code PRIVATE});
     *                   puede ser {@code null} si consulta un anónimo — en ese caso las
     *                   publicaciones {@code PRIVATE} se excluyen por completo, igual que en
     *                   {@link #findAll(boolean, String)}
     * @return lista de publicaciones serializables
     */
    @Transactional(readOnly = true)
    public List<PublicationResponse> findByAuthor(String authorId, boolean activeOnly, String viewerId) {
        expireFinishedPublications();

        if (authorId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        List<Publication> publications = activeOnly
                ? publicationRepository.findByAuthorIdAndActiveTrue(authorId)
                : publicationRepository.findByAuthorId(authorId);

        List<Publication> visible = publications.stream()
                .filter(publication -> viewerId != null || publication.getVisibility() != PublicationVisibility.PRIVATE)
                .toList();
        return toResponses(visible, viewerId);
    }

    /**
     * Recupera publicaciones para el mapa.
     * <p>
     * Por defecto solo devuelve publicaciones activas para no mostrar contenido
     * caducado o deshabilitado en la vista pública.
     * </p>
     *
     * @param activeOnly si true, devuelve solo publicaciones activas
     * @param viewerId   id de quien consulta; {@code null} si consulta un anónimo — las
     *                   publicaciones {@code PRIVATE} se excluyen por completo para anónimos (ni
     *                   siquiera se les muestra el pin); para un viewer autenticado sin acceso se
     *                   incluyen con el contenido enmascarado (ver {@code PublicationMapper})
     * @return lista de publicaciones serializables
     */
    @Transactional(readOnly = true)
    public List<PublicationResponse> findAll(boolean activeOnly, String viewerId) {
        expireFinishedPublications();

        List<Publication> publications = activeOnly
                ? publicationRepository.findByActiveTrueOrderByStartDateDesc()
                : publicationRepository.findAll();

        List<Publication> visible = publications.stream()
                .filter(publication -> viewerId != null || publication.getVisibility() != PublicationVisibility.PRIVATE)
                .toList();
        return toResponses(visible, viewerId);
    }

    /**
     * Recupera una publicación por id.
     *
     * @param id       identificador de la publicación
     * @param viewerId id de quien consulta; {@code null} si consulta un anónimo — una publicación
     *                 {@code PRIVATE} no existe para un anónimo (404, no se confirma su existencia)
     * @return respuesta serializable
     */
    @Transactional(readOnly = true)
    public PublicationResponse findById(String id, String viewerId) {
        expireFinishedPublications();

        if (id == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        Publication publication = publicationRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));
        if (viewerId == null && publication.getVisibility() == PublicationVisibility.PRIVATE) {
            throw new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND);
        }
        return toResponse(publication, viewerId);
    }

    private PublicationResponse toResponse(Publication publication, String viewerId) {
        long occupiedSlots = publicationEnrollmentRepository.countByPublicationId(publication.getId());
        return publicationMapper.toResponse(publication, occupiedSlots,
                hasAccess(publication, viewerId), accessRequestPending(publication, viewerId));
    }

    /**
     * Igual que {@link #toResponse(Publication, String)} pero para una lista completa: en vez de
     * una query de plazas/invitaciones/solicitudes por publicación (N+1, notable en
     * {@link #findAll} y {@link #findByAuthor}), trae los tres datos en una query batch por
     * colección y los consulta en memoria dentro del mapeo.
     */
    private List<PublicationResponse> toResponses(List<Publication> publications, String viewerId) {
        if (publications.isEmpty()) {
            return List.of();
        }

        List<String> publicationIds = publications.stream().map(Publication::getId).toList();
        Map<String, Long> occupiedSlotsByPublicationId = publicationEnrollmentRepository
                .findByPublicationIdIn(publicationIds).stream()
                .collect(Collectors.groupingBy(PublicationEnrollment::getPublicationId, Collectors.counting()));

        List<String> privatePublicationIds = publications.stream()
                .filter(publication -> publication.getVisibility() == PublicationVisibility.PRIVATE)
                .map(Publication::getId)
                .toList();

        boolean viewerIsAdmin = viewerId != null && !privatePublicationIds.isEmpty() && isAdmin(viewerId);
        Set<String> invitedPublicationIds = viewerId == null || privatePublicationIds.isEmpty()
                ? Set.of()
                : publicationInvitationRepository
                        .findByPublicationIdInAndInvitedUserIdAndStatusNot(
                                privatePublicationIds, viewerId, PublicationInvitationStatus.DECLINED)
                        .stream()
                        .map(PublicationInvitation::getPublicationId)
                        .collect(Collectors.toSet());
        Set<String> pendingAccessRequestPublicationIds = viewerId == null || privatePublicationIds.isEmpty()
                ? Set.of()
                : publicationAccessRequestRepository
                        .findByPublicationIdInAndRequestedByUserIdAndStatus(
                                privatePublicationIds, viewerId, PublicationAccessRequestStatus.PENDING)
                        .stream()
                        .map(PublicationAccessRequest::getPublicationId)
                        .collect(Collectors.toSet());

        return publications.stream()
                .map(publication -> publicationMapper.toResponse(publication,
                        occupiedSlotsByPublicationId.getOrDefault(publication.getId(), 0L),
                        hasAccess(publication, viewerId, viewerIsAdmin, invitedPublicationIds),
                        accessRequestPending(publication, viewerId, pendingAccessRequestPublicationIds)))
                .toList();
    }

    /**
     * Registra la inscripción del usuario autenticado en una publicación.
     * <p>
     * Reglas aplicadas:
     * </p>
     * <ul>
     * <li>Un usuario solo puede apuntarse una vez por publicación.</li>
     * <li>Si metadata.slots existe y es > 0, se aplica como aforo máximo.</li>
     * <li>No se permite apuntarse a publicaciones inactivas/finalizadas.</li>
     * </ul>
     *
     * @param publicationId id de la publicación
     * @param userId        id del usuario autenticado
     * @return estado actualizado de ocupación
     */
    public PublicationEnrollmentResponse enroll(String publicationId, String userId) {
        expireFinishedPublications();

        if (publicationId == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        if (userId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(publication.getActive())) {
            throw new ApiException("CONFLICT", "La publicación ya no está activa", HttpStatus.CONFLICT);
        }

        if (!hasAccess(publication, userId)) {
            throw new ApiException("NO_ACCESS",
                    "No tienes acceso a esta publicación privada. Solicita acceso primero.",
                    HttpStatus.FORBIDDEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        boolean alreadyEnrolled = publicationEnrollmentRepository.existsByPublicationIdAndUserId(publicationId, userId);
        if (alreadyEnrolled) {
            throw new ApiException("CONFLICT", "Ya estás apuntado a esta publicación", HttpStatus.CONFLICT);
        }

        long occupiedSlots = publicationEnrollmentRepository.countByPublicationId(publicationId);
        Integer maxSlots = resolveMaxSlots(publication);

        if (maxSlots != null && occupiedSlots >= maxSlots) {
            throw new ApiException("CONFLICT", "No hay plazas disponibles", HttpStatus.CONFLICT);
        }

        PublicationEnrollment enrollment = new PublicationEnrollment(publication.getId(), user.getId(), ZonedDateTime.now(MADRID_ZONE));
        publicationEnrollmentRepository.save(enrollment);

        long updatedOccupiedSlots = occupiedSlots + 1;
        boolean full = maxSlots != null && updatedOccupiedSlots >= maxSlots;

        return new PublicationEnrollmentResponse(publicationId, userId, updatedOccupiedSlots, maxSlots, full);
    }

    /**
     * Elimina definitivamente una publicación.
     *
     * @param publicationId id de la publicación a eliminar
     * @param requesterId   id del usuario autenticado que solicita el borrado
     */
    public void deleteById(String publicationId, String requesterId) {
        if (publicationId == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        if (requesterId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        requireAuthorOrAdmin(publication, requesterId, "eliminar esta publicación");

        publicationRepository.delete(publication);
        log.info("Publicación eliminada publicationId={} requesterId={}", publicationId, requesterId);
    }

    /**
     * Cambia la visibilidad de una publicación existente. Solo el autor o un ADMIN pueden
     * hacerlo.
     * <p>
     * {@code PRIVATE → PUBLIC} siempre está permitido. {@code → PRIVATE} se bloquea si hay
     * inscritos que no tendrían acceso bajo el nuevo modelo (no son el autor, ni ADMIN, ni tienen
     * una invitación no-{@code DECLINED}) — perderían acceso en silencio; el creador debe
     * invitarlos primero o crear una publicación nueva si quiere una versión restringida.
     * </p>
     *
     * @param publicationId id de la publicación
     * @param requesterId   id del usuario autenticado (autor o ADMIN)
     * @param request       visibilidad destino
     * @return publicación actualizada, vista desde el propio autor
     */
    public PublicationResponse changeVisibility(String publicationId, String requesterId, ChangeVisibilityRequest request) {
        if (publicationId == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        if (requesterId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }
        if (request == null || request.visibility() == null) {
            throw new ApiException("BAD_REQUEST", "visibility requerida", HttpStatus.BAD_REQUEST);
        }

        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        requireAuthorOrAdmin(publication, requesterId, "cambiar la visibilidad de esta publicación");

        if (request.visibility() == PublicationVisibility.PUBLIC) {
            publication.setVisibility(PublicationVisibility.PUBLIC);
        } else {
            long foreignCount = publicationEnrollmentRepository.findByPublicationId(publicationId).stream()
                    .map(PublicationEnrollment::getUserId)
                    .filter(userId -> !userId.equals(publication.getAuthorId())
                            && !isAdmin(userId)
                            && !publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                                    publicationId, userId, PublicationInvitationStatus.DECLINED))
                    .count();
            if (foreignCount > 0) {
                throw new ApiException("FOREIGN_ENROLLMENTS",
                        "No puedes hacer privada esta publicación: hay " + foreignCount
                                + " persona(s) apuntada(s) sin invitación. Invítalas primero o crea una publicación"
                                + " nueva si quieres una versión restringida.",
                        HttpStatus.CONFLICT);
            }

            publication.setVisibility(PublicationVisibility.PRIVATE);
        }

        Publication saved = publicationRepository.save(publication);
        log.info("Visibilidad cambiada publicationId={} requesterId={} visibility={}",
                publicationId, requesterId, saved.getVisibility());
        return toResponse(saved, requesterId);
    }

    /**
     * Solicita acceso a una publicación privada de la que no se tiene acceso todavía. La aprueba
     * el autor de la publicación (ver {@link PublicationAccessRequest}).
     *
     * @param publicationId id de la publicación privada
     * @param userId        id del usuario autenticado que solicita
     * @return solicitud de acceso creada
     */
    public PublicationAccessRequestResponse requestAccess(String publicationId, String userId) {
        if (publicationId == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        if (userId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        if (publication.getVisibility() != PublicationVisibility.PRIVATE) {
            throw new ApiException("BAD_REQUEST", "Esta publicación no requiere invitación", HttpStatus.BAD_REQUEST);
        }
        if (userId.equals(publication.getAuthorId())) {
            throw new ApiException("BAD_REQUEST", "Ya eres el autor de esta publicación", HttpStatus.BAD_REQUEST);
        }
        if (hasAccess(publication, userId)) {
            throw new ApiException("ALREADY_HAS_ACCESS", "Ya tienes acceso a esta publicación", HttpStatus.CONFLICT);
        }
        if (publicationAccessRequestRepository.existsByPublicationIdAndRequestedByUserIdAndStatus(
                publicationId, userId, PublicationAccessRequestStatus.PENDING)) {
            throw new ApiException("ALREADY_REQUESTED", "Ya has solicitado acceso a esta publicación",
                    HttpStatus.CONFLICT);
        }

        MapItUser requester = userService.getByIdOrThrow(userId);

        PublicationAccessRequest request = new PublicationAccessRequest();
        request.setPublicationId(publicationId);
        request.setRequestedByUserId(userId);
        request.setStatus(PublicationAccessRequestStatus.PENDING);
        request.setCreatedAt(Instant.now());
        PublicationAccessRequest saved = publicationAccessRequestRepository.save(request);

        MapItUser author = userService.getByIdOrThrow(publication.getAuthorId());
        notificationService.notifyPublicationAccessRequest(author, publication.getTitle(), requester);

        log.info("Solicitud de acceso creada requestId={} publicationId={} requestedByUserId={}",
                saved.getId(), publicationId, userId);

        return buildAccessRequestResponse(saved, publication, requester);
    }

    /**
     * Lista las solicitudes de acceso pendientes de una publicación. Solo el autor (o un ADMIN)
     * puede verlas.
     *
     * @param publicationId id de la publicación
     * @param requesterId   id del usuario autenticado que consulta (autor o ADMIN)
     * @return solicitudes pendientes, más recientes primero
     */
    @Transactional(readOnly = true)
    public List<PublicationAccessRequestResponse> getPendingAccessRequests(String publicationId, String requesterId) {
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        requireAuthorOrAdmin(publication, requesterId, "ver las solicitudes de acceso de esta publicación");

        return publicationAccessRequestRepository
                .findByPublicationIdAndStatus(publicationId, PublicationAccessRequestStatus.PENDING)
                .stream()
                .map(request -> buildAccessRequestResponse(request, publication,
                        userService.getByIdOrThrow(request.getRequestedByUserId())))
                .sorted(Comparator.comparing(PublicationAccessRequestResponse::createdAt).reversed())
                .toList();
    }

    /**
     * Acepta una solicitud de acceso. Solo el autor de la publicación (o un ADMIN) puede
     * hacerlo. El acceso concedido se materializa como una {@link PublicationInvitation} ya
     * {@code ACCEPTED} — mismo mecanismo que una invitación directa del autor, para que
     * {@link #enroll} no necesite un tercer camino de comprobación de acceso.
     *
     * @param requestId   id de la solicitud
     * @param requesterId id del usuario autenticado (autor de la publicación, o ADMIN)
     * @return solicitud actualizada
     */
    public PublicationAccessRequestResponse acceptAccessRequest(String requestId, String requesterId) {
        PublicationAccessRequest request = publicationAccessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Solicitud no encontrada", HttpStatus.NOT_FOUND));
        Publication publication = publicationRepository.findById(request.getPublicationId())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        requireAuthorOrAdmin(publication, requesterId, "aceptar esta solicitud de acceso");

        if (request.getStatus() != PublicationAccessRequestStatus.PENDING) {
            throw new ApiException("CONFLICT", "Esta solicitud ya ha sido resuelta", HttpStatus.CONFLICT);
        }

        request.setStatus(PublicationAccessRequestStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());
        PublicationAccessRequest saved = publicationAccessRequestRepository.save(request);

        PublicationInvitation invitation = new PublicationInvitation();
        invitation.setPublicationId(publication.getId());
        invitation.setInvitedUserId(request.getRequestedByUserId());
        invitation.setInvitedByUserId(requesterId);
        invitation.setStatus(PublicationInvitationStatus.ACCEPTED);
        invitation.setCreatedAt(Instant.now());
        publicationInvitationRepository.save(invitation);

        MapItUser requester = userService.getByIdOrThrow(request.getRequestedByUserId());
        notificationService.notifyPublicationAccessRequestResolved(requester, publication.getTitle(), true);

        log.info("Solicitud de acceso aceptada requestId={} publicationId={} requestedByUserId={}",
                saved.getId(), publication.getId(), request.getRequestedByUserId());

        return buildAccessRequestResponse(saved, publication, requester);
    }

    /**
     * Rechaza una solicitud de acceso. Solo el autor de la publicación (o un ADMIN) puede
     * hacerlo.
     *
     * @param requestId   id de la solicitud
     * @param requesterId id del usuario autenticado (autor de la publicación, o ADMIN)
     * @return solicitud actualizada
     */
    public PublicationAccessRequestResponse rejectAccessRequest(String requestId, String requesterId) {
        PublicationAccessRequest request = publicationAccessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Solicitud no encontrada", HttpStatus.NOT_FOUND));
        Publication publication = publicationRepository.findById(request.getPublicationId())
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        requireAuthorOrAdmin(publication, requesterId, "rechazar esta solicitud de acceso");

        if (request.getStatus() != PublicationAccessRequestStatus.PENDING) {
            throw new ApiException("CONFLICT", "Esta solicitud ya ha sido resuelta", HttpStatus.CONFLICT);
        }

        request.setStatus(PublicationAccessRequestStatus.REJECTED);
        request.setRespondedAt(Instant.now());
        PublicationAccessRequest saved = publicationAccessRequestRepository.save(request);

        MapItUser requester = userService.getByIdOrThrow(request.getRequestedByUserId());
        notificationService.notifyPublicationAccessRequestResolved(requester, publication.getTitle(), false);

        log.info("Solicitud de acceso rechazada requestId={} publicationId={} requestedByUserId={}",
                saved.getId(), publication.getId(), request.getRequestedByUserId());

        return buildAccessRequestResponse(saved, publication, requester);
    }

    private PublicationAccessRequestResponse buildAccessRequestResponse(PublicationAccessRequest request,
            Publication publication, MapItUser requester) {
        return new PublicationAccessRequestResponse(
                request.getId(),
                request.getPublicationId(),
                publication.getTitle(),
                request.getRequestedByUserId(),
                requester.getName(),
                requester.getNick(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getRespondedAt());
    }

    private void requireAuthorOrAdmin(Publication publication, String requesterId, String action) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        boolean isAuthor = requesterId.equals(publication.getAuthorId());
        boolean isAdmin = requester.getUserType() == UserType.ADMIN;
        if (!isAuthor && !isAdmin) {
            throw new ApiException("FORBIDDEN", "No tienes permisos para " + action, HttpStatus.FORBIDDEN);
        }
    }

    /**
     * true si {@code viewerId} puede ver el contenido completo de {@code publication}: siempre en
     * {@code PUBLIC}; en {@code PRIVATE}, si es el autor, un ADMIN, o tiene una
     * {@link PublicationInvitation} en estado distinto de {@code DECLINED}. Invitar a los
     * integrantes de un grupo es solo un atajo de cliente para rellenar {@code inviteUserIds} — la
     * pertenencia a un grupo nunca es, por sí sola, una vía de acceso.
     */
    private boolean hasAccess(Publication publication, String viewerId) {
        if (publication.getVisibility() != PublicationVisibility.PRIVATE) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        if (viewerId.equals(publication.getAuthorId()) || isAdmin(viewerId)) {
            return true;
        }
        return publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                publication.getId(), viewerId, PublicationInvitationStatus.DECLINED);
    }

    /** Variante de {@link #hasAccess(Publication, String)} para listas, ver {@link #toResponses}. */
    private boolean hasAccess(Publication publication, String viewerId, boolean viewerIsAdmin,
            Set<String> invitedPublicationIds) {
        if (publication.getVisibility() != PublicationVisibility.PRIVATE) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        if (viewerId.equals(publication.getAuthorId()) || viewerIsAdmin) {
            return true;
        }
        return invitedPublicationIds.contains(publication.getId());
    }

    private boolean isAdmin(String userId) {
        return userRepository.findById(userId)
                .map(user -> user.getUserType() == UserType.ADMIN)
                .orElse(false);
    }

    /**
     * true si {@code viewerId} tiene una {@link PublicationAccessRequest} pendiente sobre
     * {@code publication}. {@code null} si la publicación es {@code PUBLIC} o no hay viewer
     * (anónimo) — en ambos casos no aplica.
     */
    private Boolean accessRequestPending(Publication publication, String viewerId) {
        if (publication.getVisibility() != PublicationVisibility.PRIVATE || viewerId == null) {
            return null;
        }
        return publicationAccessRequestRepository.existsByPublicationIdAndRequestedByUserIdAndStatus(
                publication.getId(), viewerId, PublicationAccessRequestStatus.PENDING);
    }

    /** Variante de {@link #accessRequestPending(Publication, String)} para listas, ver {@link #toResponses}. */
    private Boolean accessRequestPending(Publication publication, String viewerId,
            Set<String> pendingAccessRequestPublicationIds) {
        if (publication.getVisibility() != PublicationVisibility.PRIVATE || viewerId == null) {
            return null;
        }
        return pendingAccessRequestPublicationIds.contains(publication.getId());
    }

    /**
     * Marca como finalizadas las publicaciones activas con fecha de fin vencida.
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

    /**
     * Elimina definitivamente las publicaciones caducadas hace más de
     * {@value #EXPIRED_RETENTION_MONTHS} meses (endDate vencida; las promociones
     * indefinidas con endDate null nunca se ven afectadas).
     * <p>
     * Corre diariamente vía {@code @Scheduled}; no depende de que
     * {@link #expireFinishedPublications()} haya marcado antes la publicación
     * como inactiva, ya que ambos jobs se basan directamente en endDate.
     * </p>
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Europe/Madrid")
    public void deleteExpiredPublications() {
        ZonedDateTime cutoff = ZonedDateTime.now(MADRID_ZONE).minusMonths(EXPIRED_RETENTION_MONTHS);
        List<Publication> expired = publicationRepository.findExpiredSince(cutoff);
        if (expired.isEmpty()) {
            return;
        }

        publicationRepository.deleteAll(expired);
        log.info("Eliminadas {} publicaciones caducadas hace más de {} meses", expired.size(),
                EXPIRED_RETENTION_MONTHS);
    }

    private Integer resolveMaxSlots(Publication publication) {
        if (publication.getMetadata() == null)
            return null;

        Object rawSlots = publication.getMetadata().get("slots");
        if (!(rawSlots instanceof Number number))
            return null;

        int slots = number.intValue();
        return slots > 0 ? slots : null;
    }

        /**
     * Desapunta al usuario autenticado de una publicación.
     *
     * @param publicationId id de la publicación
     * @param userId        id del usuario autenticado
     */
    public void unenroll(String publicationId, String userId) {
        if (publicationId == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        if (userId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Usuario no encontrado", HttpStatus.NOT_FOUND));

        PublicationEnrollment enrollment = publicationEnrollmentRepository
                .findByPublicationIdAndUserId(publicationId, userId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "No estás apuntado a esta publicación", HttpStatus.NOT_FOUND));

        publicationEnrollmentRepository.delete(enrollment);
    }

    /**
     * Obtiene la lista de usuarios inscritos en una publicación.
     *
     * @param publicationId identificador de la publicación
     * @param viewerId      id del usuario autenticado que consulta (esta ruta requiere sesión)
     * @return lista de DTOs con userId, userName y fecha de inscripción
     */
    public List<EnrollmentDto> getEnrollments(String publicationId, String viewerId) {
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        if (!hasAccess(publication, viewerId)) {
            throw new ApiException("FORBIDDEN", "No puedes ver la lista de apuntados de esta publicación",
                    HttpStatus.FORBIDDEN);
        }

        return publicationEnrollmentRepository.findByPublicationId(publicationId)
                .stream()
                .map(enrollment -> {
                    String userName = userRepository.findById(enrollment.getUserId())
                            .map(User::getName).orElse("Unknown");
                    return new EnrollmentDto(
                            enrollment.getUserId(),
                            userName,
                            enrollment.getCreatedAt());
                })
                .sorted(Comparator.comparing(EnrollmentDto::enrolledAt).reversed())
                .toList();
    }
}
