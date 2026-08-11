package emc.mapIt.service;

import emc.mapIt.dto.ChangeVisibilityRequest;
import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.EnrollmentDto;
import emc.mapIt.dto.PublicationEnrollmentResponse;
import emc.mapIt.dto.PublicationResponse;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationEnrollment;
import emc.mapIt.entity.PublicationInvitationStatus;
import emc.mapIt.entity.PublicationVisibility;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.groups.GroupJoinRequestResponse;
import emc.mapIt.groups.GroupMembershipSummary;
import emc.mapIt.groups.GroupService;
import emc.mapIt.mapper.PublicationMapper;
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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final UserRepository userRepository;
    private final LocationTypeRepository locationTypeRepository;
    private final PublicationMapper publicationMapper;
    private final GroupService groupService;
    private final PublicationInvitationDispatcher publicationInvitationDispatcher;

    /**
     * Constructor para inyección de dependencias.
     */
    public PublicationService(PublicationRepository publicationRepository,
            PublicationEnrollmentRepository publicationEnrollmentRepository,
            PublicationInvitationRepository publicationInvitationRepository,
            UserRepository userRepository,
            LocationTypeRepository locationTypeRepository,
            PublicationMapper publicationMapper,
            GroupService groupService,
            PublicationInvitationDispatcher publicationInvitationDispatcher) {
        this.publicationRepository = publicationRepository;
        this.publicationEnrollmentRepository = publicationEnrollmentRepository;
        this.publicationInvitationRepository = publicationInvitationRepository;
        this.userRepository = userRepository;
        this.locationTypeRepository = locationTypeRepository;
        this.publicationMapper = publicationMapper;
        this.groupService = groupService;
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

        PublicationVisibility visibility = request.visibility() != null
                ? request.visibility() : PublicationVisibility.PUBLIC;
        boolean hasGroupId = request.groupId() != null && !request.groupId().isBlank();
        // groupId es opcional incluso en PRIVATE_GROUP: una publicación privada puede nacer
        // "solo invitados" (sin grupo vinculado), confiando únicamente en inviteUserIds — el
        // organizador puede vincularla a un grupo más adelante desde la edición. Si se informa,
        // sigue exigiendo que sea un grupo propio.
        if (visibility == PublicationVisibility.PRIVATE_GROUP && hasGroupId
                && !groupService.isOrganizer(request.groupId(), authorId)) {
            throw new ApiException("FORBIDDEN",
                    "Solo puedes crear publicaciones privadas para grupos que organizas",
                    HttpStatus.FORBIDDEN);
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
        // de la visibilidad — en PUBLIC es solo un aviso, en PRIVATE_GROUP además concede acceso
        // para apuntarse (ver #enroll).
        if (request.inviteUserIds() != null && !request.inviteUserIds().isEmpty()) {
            Set<String> invitees = new LinkedHashSet<>(request.inviteUserIds());
            invitees.remove(authorId);
            if (!invitees.isEmpty()) {
                publicationInvitationDispatcher.dispatchAsync(saved, authorId, invitees);
            }
        }

        // El autor de una publicación privada siempre es organizador (y por tanto miembro) del
        // grupo — ya validado arriba — así que nunca hay que enmascararle su propia creación.
        return publicationMapper.toResponse(saved, 1, resolveGroupInfo(saved, authorId));
    }

    /**
     * Recupera publicaciones por autor.
     *
     * @param authorId   id del usuario
     * @param activeOnly si true, filtra solo activas
     * @param viewerId   id de quien consulta (para calcular pertenencia a grupo en publicaciones
     *                   privadas); puede ser {@code null} si consulta un anónimo
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

        return publications.stream()
                .map(publication -> publicationMapper.toResponse(publication,
                        publicationEnrollmentRepository.countByPublicationId(publication.getId()),
                        resolveGroupInfo(publication, viewerId)))
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
     * @param viewerId   id de quien consulta (para calcular pertenencia a grupo en publicaciones
     *                   privadas); puede ser {@code null} si consulta un anónimo — las
     *                   publicaciones privadas siguen siendo visibles, solo se enmascara el aforo
     * @return lista de publicaciones serializables
     */
    @Transactional(readOnly = true)
    public List<PublicationResponse> findAll(boolean activeOnly, String viewerId) {
        expireFinishedPublications();

        List<Publication> publications = activeOnly
                ? publicationRepository.findByActiveTrueOrderByStartDateDesc()
                : publicationRepository.findAll();

        return publications.stream()
                .map(publication -> publicationMapper.toResponse(publication,
                        publicationEnrollmentRepository.countByPublicationId(publication.getId()),
                        resolveGroupInfo(publication, viewerId)))
                .toList();
    }

    /**
     * Recupera una publicación por id.
     *
     * @param id       identificador de la publicación
     * @param viewerId id de quien consulta (para calcular pertenencia a grupo en publicaciones
     *                 privadas); puede ser {@code null} si consulta un anónimo
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
        long occupiedSlots = publicationEnrollmentRepository.countByPublicationId(publication.getId());
        return publicationMapper.toResponse(publication, occupiedSlots, resolveGroupInfo(publication, viewerId));
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

        if (publication.getVisibility() == PublicationVisibility.PRIVATE_GROUP
                && !groupService.isMember(publication.getGroupId(), userId)
                && !publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                        publicationId, userId, PublicationInvitationStatus.DECLINED)) {
            throw new ApiException("NOT_GROUP_MEMBER",
                    "Debes pertenecer al grupo o tener una invitación para apuntarte. Solicita acceso primero.",
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
     * {@code PRIVATE_GROUP → PUBLIC} siempre está permitido. {@code → PRIVATE_GROUP} (desde
     * público o desde otro grupo privado) se bloquea si hay inscritos que no son miembros del
     * grupo destino — perderían acceso en silencio; el creador debe crear una publicación nueva
     * si quiere una versión restringida.
     * </p>
     *
     * @param publicationId id de la publicación
     * @param requesterId   id del usuario autenticado (autor o ADMIN)
     * @param request       visibilidad destino y, si aplica, grupo
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
            publication.setGroupId(null);
        } else {
            if (request.groupId() == null || request.groupId().isBlank()) {
                throw new ApiException("BAD_REQUEST", "groupId requerido", HttpStatus.BAD_REQUEST);
            }
            if (!groupService.isOrganizer(request.groupId(), requesterId)) {
                throw new ApiException("FORBIDDEN", "Solo puedes usar un grupo que organizas", HttpStatus.FORBIDDEN);
            }

            Set<String> memberIds = groupService.getMemberUserIds(request.groupId());
            long foreignCount = publicationEnrollmentRepository.findByPublicationId(publicationId).stream()
                    .map(PublicationEnrollment::getUserId)
                    .filter(userId -> !memberIds.contains(userId))
                    .count();
            if (foreignCount > 0) {
                throw new ApiException("FOREIGN_ENROLLMENTS",
                        "No puedes limitar esta publicación a ese grupo: hay " + foreignCount
                                + " persona(s) apuntada(s) que no son miembros. Crea una publicación nueva si quieres"
                                + " una versión solo para el grupo.",
                        HttpStatus.CONFLICT);
            }

            publication.setVisibility(PublicationVisibility.PRIVATE_GROUP);
            publication.setGroupId(request.groupId());
        }

        Publication saved = publicationRepository.save(publication);
        long occupiedSlots = publicationEnrollmentRepository.countByPublicationId(publicationId);
        log.info("Visibilidad cambiada publicationId={} requesterId={} visibility={}",
                publicationId, requesterId, saved.getVisibility());
        return publicationMapper.toResponse(saved, occupiedSlots, resolveGroupInfo(saved, requesterId));
    }

    /**
     * Solicita acceso al grupo de una publicación privada, para poder apuntarse después. Delega
     * en {@link GroupService#requestToJoin} — la solicitud vive en el dominio Grupos, esta
     * publicación solo queda como referencia de trazabilidad.
     *
     * @param publicationId id de la publicación privada
     * @param userId        id del usuario autenticado que solicita
     * @return solicitud de acceso creada
     */
    public GroupJoinRequestResponse requestAccess(String publicationId, String userId) {
        if (publicationId == null) {
            throw new ApiException("BAD_REQUEST", "ID de publicación requerido", HttpStatus.BAD_REQUEST);
        }
        if (userId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }

        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Publicación no encontrada", HttpStatus.NOT_FOUND));

        if (publication.getVisibility() != PublicationVisibility.PRIVATE_GROUP) {
            throw new ApiException("BAD_REQUEST", "Esta publicación no requiere invitación", HttpStatus.BAD_REQUEST);
        }

        return groupService.requestToJoin(userId, publication.getGroupId(), publicationId);
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
     * Resuelve el resumen de pertenencia al grupo de una publicación privada para el espectador
     * indicado. {@code null} si la publicación es pública (o {@code visibility} viene {@code null}
     * — documento legacy anterior a este campo, se trata igual que {@code PUBLIC}).
     */
    private GroupMembershipSummary resolveGroupInfo(Publication publication, String viewerId) {
        if (publication.getVisibility() != PublicationVisibility.PRIVATE_GROUP) {
            return null;
        }
        return groupService.getMembershipSummary(publication.getGroupId(), viewerId);
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

        if (publication.getVisibility() == PublicationVisibility.PRIVATE_GROUP
                && !groupService.isMember(publication.getGroupId(), viewerId)) {
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
