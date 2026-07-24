package emc.mapIt.groups;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.exception.ApiException;
import emc.mapIt.notifications.NotificationSender;
import emc.mapIt.repository.MainCategoryRepository;
import emc.mapIt.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Servicio de aplicación del dominio Grupos: alta/edición, invitaciones, aceptación/rechazo,
 * abandono y aviso al organizador.
 * <p>
 * Depende de {@link UserService} (no de {@code UserRepository}) para resolver/validar usuarios —
 * respeta el límite de módulo fijado en {@code docs/ARQUITECTURA.md} (los módulos se comunican a
 * través de servicios públicos, no de repositorios ajenos). La validación de categoría contra
 * {@link MainCategoryRepository} sí es acceso directo, igual que {@code PublicationService} hace
 * con {@code LocationTypeRepository}: es una entidad de solo lectura sin lógica de dominio
 * propia, no un módulo con reglas de negocio que proteger.
 * </p>
 */
@Service
@Transactional
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final MainCategoryRepository mainCategoryRepository;
    private final UserService userService;
    private final NotificationSender notificationSender;
    private final GroupMapper groupMapper;
    private final GroupInvitationMapper groupInvitationMapper;

    /**
     * Constructor para inyección de dependencias.
     */
    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInvitationRepository groupInvitationRepository,
            MainCategoryRepository mainCategoryRepository,
            UserService userService,
            NotificationSender notificationSender,
            GroupMapper groupMapper,
            GroupInvitationMapper groupInvitationMapper) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.mainCategoryRepository = mainCategoryRepository;
        this.userService = userService;
        this.notificationSender = notificationSender;
        this.groupMapper = groupMapper;
        this.groupInvitationMapper = groupInvitationMapper;
    }

    // ── Grupos ───────────────────────────────────────────────────────────────

    /**
     * Crea un grupo nuevo. El usuario autenticado pasa a ser su organizador (primer
     * {@link GroupMember}) y, si se indican {@code inviteUserIds}, se emite una invitación por
     * cada uno (se ignoran duplicados y el propio organizador si aparece en la lista).
     *
     * @param organizerId id del usuario autenticado que crea el grupo
     * @param request     payload de creación
     * @return grupo creado, con el organizador ya como único miembro
     */
    public GroupResponse createGroup(String organizerId, CreateGroupRequest request) {
        requireUserId(organizerId);
        if (request == null) {
            throw new ApiException("BAD_REQUEST", "Request de grupo requerida", HttpStatus.BAD_REQUEST);
        }

        userService.getByIdOrThrow(organizerId);
        validateCategoryExists(request.categoryId());

        Group group = groupMapper.toEntity(request, organizerId);
        Group saved = groupRepository.save(group);

        GroupMember organizerMember = new GroupMember();
        organizerMember.setGroupId(saved.getId());
        organizerMember.setUserId(organizerId);
        organizerMember.setRole(GroupRole.ORGANIZER);
        organizerMember.setJoinedAt(saved.getCreatedAt());
        groupMemberRepository.save(organizerMember);

        Set<String> initialInvitees = new LinkedHashSet<>(
                request.inviteUserIds() == null ? List.of() : request.inviteUserIds());
        initialInvitees.remove(organizerId);
        initialInvitees.forEach(invitedUserId -> sendInvitation(saved, organizerId, invitedUserId));

        Set<String> initialEmailInvitees = new LinkedHashSet<>();
        if (request.inviteEmails() != null) {
            request.inviteEmails().forEach(email -> initialEmailInvitees.add(normalizeEmail(email)));
        }
        initialEmailInvitees.forEach(email -> sendInvitationByEmail(saved, organizerId, email));

        log.info("Grupo creado groupId={} organizerId={} invitacionesIniciales={} invitacionesEmailIniciales={}",
                saved.getId(), organizerId, initialInvitees.size(), initialEmailInvitees.size());
        return buildGroupResponse(saved);
    }

    /**
     * Actualiza nombre, descripción y/o categoría de un grupo. Solo el organizador puede hacerlo.
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo a editar
     * @param request     campos a actualizar (todos opcionales)
     * @return grupo actualizado
     */
    public GroupResponse updateGroup(String requesterId, String groupId, UpdateGroupRequest request) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireOrganizer(group, requesterId);

        if (request == null) {
            return buildGroupResponse(group);
        }

        if (request.name() != null && !request.name().isBlank()) {
            group.setName(request.name().trim());
        }
        if (request.description() != null) {
            group.setDescription(request.description().trim());
        }
        if (request.categoryId() != null && !request.categoryId().isBlank()) {
            validateCategoryExists(request.categoryId());
            group.setCategoryId(request.categoryId());
        }
        group.setUpdatedAt(Instant.now());

        Group saved = groupRepository.save(group);
        log.info("Grupo actualizado groupId={} requesterId={}", groupId, requesterId);
        return buildGroupResponse(saved);
    }

    /**
     * Recupera el detalle de un grupo. Solo sus miembros pueden verlo.
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo
     * @return grupo con su lista de miembros resuelta
     */
    @Transactional(readOnly = true)
    public GroupResponse getGroupById(String requesterId, String groupId) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireMember(group, requesterId);
        return buildGroupResponse(group);
    }

    /**
     * Lista los grupos del usuario autenticado, sea organizador o miembro.
     *
     * @param userId id del usuario autenticado
     * @return grupos a los que pertenece
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getMyGroups(String userId) {
        requireUserId(userId);
        return groupMemberRepository.findByUserId(userId).stream()
                .map(membership -> groupRepository.findById(membership.getGroupId()))
                .flatMap(Optional::stream)
                .map(this::buildGroupResponse)
                .toList();
    }

    // ── Invitaciones ─────────────────────────────────────────────────────────

    /**
     * Invita a un usuario ya existente a un grupo. Solo el organizador puede invitar.
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo
     * @param request     id del usuario a invitar
     * @return invitación creada
     */
    public GroupInvitationResponse inviteUser(String requesterId, String groupId, InviteUserRequest request) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireOrganizer(group, requesterId);

        if (request == null || request.userId() == null || request.userId().isBlank()) {
            throw new ApiException("BAD_REQUEST", "userId requerido", HttpStatus.BAD_REQUEST);
        }

        GroupInvitation invitation = sendInvitation(group, requesterId, request.userId());
        log.info("Invitación creada groupId={} invitedUserId={} invitedByUserId={}",
                groupId, request.userId(), requesterId);
        return buildInvitationResponse(invitation);
    }

    /**
     * Invita por email a alguien que puede no estar registrado todavía. Solo el organizador
     * puede invitar. Si el email ya pertenece a un usuario, se resuelve como una invitación
     * normal ({@link #sendInvitation}); si no, queda pendiente de reclamar cuando esa persona
     * se registre y verifique su correo (ver {@link GroupInvitation}).
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo
     * @param request     email a invitar
     * @return invitación creada
     */
    public GroupInvitationResponse inviteByEmail(String requesterId, String groupId, InviteByEmailRequest request) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireOrganizer(group, requesterId);

        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new ApiException("BAD_REQUEST", "email requerido", HttpStatus.BAD_REQUEST);
        }

        GroupInvitation invitation = sendInvitationByEmail(group, requesterId, request.email());
        log.info("Invitación por email creada groupId={} invitedByUserId={}", groupId, requesterId);
        return buildInvitationResponse(invitation);
    }

    /**
     * Lista las invitaciones pendientes del usuario autenticado. Antes de leer, reclama
     * cualquier invitación por email que coincida con su correo verificado (ver
     * {@link GroupInvitation}) — por eso escribe y no es de solo lectura.
     *
     * @param userId id del usuario autenticado
     * @return invitaciones en estado PENDING
     */
    public List<GroupInvitationResponse> getPendingInvitations(String userId) {
        requireUserId(userId);
        claimEmailInvitations(userId);
        return groupInvitationRepository.findByInvitedUserIdAndStatus(userId, GroupInvitationStatus.PENDING)
                .stream()
                .map(this::buildInvitationResponse)
                .toList();
    }

    /**
     * Vincula a {@code userId} cualquier invitación por email pendiente que coincida con su
     * dirección verificada. No reclama nada si el email todavía no está verificado, para que
     * nadie se apropie de una invitación registrándose con un correo que no controla.
     */
    private void claimEmailInvitations(String userId) {
        MapItUser user = userService.getByIdOrThrow(userId);
        if (!user.isEmailVerified()) {
            return;
        }
        List<GroupInvitation> unclaimed = groupInvitationRepository
                .findByInvitedEmailAndStatus(normalizeEmail(user.getEmail()), GroupInvitationStatus.PENDING);
        for (GroupInvitation invitation : unclaimed) {
            invitation.setInvitedUserId(userId);
            invitation.setInvitedEmail(null);
            groupInvitationRepository.save(invitation);
            log.info("Invitación por email reclamada invitationId={} userId={}", invitation.getId(), userId);
        }
    }

    /**
     * Recupera el detalle de una invitación (pantalla a la que lleva el enlace del correo). Solo
     * el usuario invitado puede verla.
     *
     * @param requesterId  id del usuario autenticado
     * @param invitationId id de la invitación
     * @return detalle de la invitación
     */
    @Transactional(readOnly = true)
    public GroupInvitationResponse getInvitationById(String requesterId, String invitationId) {
        requireUserId(requesterId);
        GroupInvitation invitation = getInvitationOrThrow(invitationId);
        requireInvitationOwner(invitation, requesterId);
        return buildInvitationResponse(invitation);
    }

    /**
     * Acepta una invitación pendiente: añade al usuario como {@link GroupRole#MEMBER} del grupo.
     * Idempotente si, por alguna carrera, el usuario ya fuera miembro.
     *
     * @param requesterId  id del usuario autenticado (debe coincidir con el invitado)
     * @param invitationId id de la invitación
     * @return grupo al que se ha unido
     */
    public GroupResponse acceptInvitation(String requesterId, String invitationId) {
        requireUserId(requesterId);
        GroupInvitation invitation = getInvitationOrThrow(invitationId);
        requireInvitationOwner(invitation, requesterId);
        requirePendingInvitation(invitation);

        Group group = getGroupOrThrow(invitation.getGroupId());

        if (!groupMemberRepository.existsByGroupIdAndUserId(group.getId(), requesterId)) {
            GroupMember member = new GroupMember();
            member.setGroupId(group.getId());
            member.setUserId(requesterId);
            member.setRole(GroupRole.MEMBER);
            member.setJoinedAt(Instant.now());
            groupMemberRepository.save(member);
        }

        invitation.setStatus(GroupInvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());
        groupInvitationRepository.save(invitation);

        log.info("Invitación aceptada invitationId={} userId={} groupId={}",
                invitationId, requesterId, group.getId());
        return buildGroupResponse(group);
    }

    /**
     * Rechaza una invitación pendiente. No añade al usuario al grupo.
     *
     * @param requesterId  id del usuario autenticado (debe coincidir con el invitado)
     * @param invitationId id de la invitación
     */
    public void declineInvitation(String requesterId, String invitationId) {
        requireUserId(requesterId);
        GroupInvitation invitation = getInvitationOrThrow(invitationId);
        requireInvitationOwner(invitation, requesterId);
        requirePendingInvitation(invitation);

        invitation.setStatus(GroupInvitationStatus.DECLINED);
        invitation.setRespondedAt(Instant.now());
        groupInvitationRepository.save(invitation);

        log.info("Invitación rechazada invitationId={} userId={}", invitationId, requesterId);
    }

    // ── Pertenencia / comunicación ───────────────────────────────────────────

    /**
     * Elimina un grupo permanentemente. Solo el organizador puede hacerlo.
     * Borra en cascada todos los miembros e invitaciones asociados antes de eliminar el grupo.
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo a eliminar
     */
    public void deleteGroup(String requesterId, String groupId) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireOrganizer(group, requesterId);

        groupMemberRepository.deleteByGroupId(groupId);
        groupInvitationRepository.deleteByGroupId(groupId);
        groupRepository.delete(group);

        log.info("Grupo eliminado groupId={} organizerId={}", groupId, requesterId);
    }

    /**
     * Abandona un grupo del que se es miembro. El organizador no puede salir de su propio grupo
     * (haría falta transferir la organización antes, funcionalidad no implementada todavía).
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo
     */
    public void leaveGroup(String requesterId, String groupId) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);

        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, requesterId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "No perteneces a este grupo", HttpStatus.NOT_FOUND));

        if (membership.getRole() == GroupRole.ORGANIZER) {
            throw new ApiException("FORBIDDEN",
                    "El organizador no puede salir de su propio grupo", HttpStatus.FORBIDDEN);
        }

        groupMemberRepository.delete(membership);
        log.info("Usuario salió del grupo groupId={} userId={}", group.getId(), requesterId);
    }

    /**
     * Cancela una invitación pendiente. Puede hacerlo el organizador del grupo o el propio
     * invitado. La invitación se elimina definitivamente (no queda registro visible en la UI).
     *
     * @param requesterId  id del usuario autenticado
     * @param invitationId id de la invitación a cancelar
     */
    public void cancelInvitation(String requesterId, String invitationId) {
        requireUserId(requesterId);
        GroupInvitation invitation = getInvitationOrThrow(invitationId);
        requirePendingInvitation(invitation);

        Group group = getGroupOrThrow(invitation.getGroupId());
        boolean isOrganizer = group.getOrganizerId().equals(requesterId);
        // Una invitación por email no reclamada no tiene invitado real todavía que también
        // pueda cancelarla — solo el organizador.
        boolean isInvitee = invitation.getInvitedUserId() != null
                && invitation.getInvitedUserId().equals(requesterId);
        if (!isOrganizer && !isInvitee) {
            throw new ApiException("FORBIDDEN", "No puedes cancelar esta invitación", HttpStatus.FORBIDDEN);
        }

        groupInvitationRepository.delete(invitation);
        log.info("Invitación cancelada invitationId={} requesterId={}", invitationId, requesterId);
    }

    /**
     * Expulsa a un miembro del grupo. Solo el organizador puede hacerlo; no puede expulsarse
     * a sí mismo (usaría {@link #deleteGroup} o la transferencia de organización).
     *
     * @param requesterId  id del usuario autenticado (debe ser el organizador)
     * @param groupId      id del grupo
     * @param targetUserId id del miembro a expulsar
     */
    public void removeMember(String requesterId, String groupId, String targetUserId) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireOrganizer(group, requesterId);

        if (requesterId.equals(targetUserId)) {
            throw new ApiException("BAD_REQUEST",
                    "El organizador no puede expulsarse a sí mismo", HttpStatus.BAD_REQUEST);
        }

        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "El usuario no es miembro del grupo", HttpStatus.NOT_FOUND));

        groupMemberRepository.delete(membership);
        log.info("Miembro expulsado groupId={} targetUserId={} organizerId={}", groupId, targetUserId, requesterId);
    }

    /**
     * Envía un aviso (email) al organizador de un grupo. Solo un miembro (no el propio
     * organizador) puede enviarlo.
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo
     * @param request     mensaje a enviar
     */
    public void notifyOrganizer(String requesterId, String groupId, NotifyOrganizerRequest request) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireMember(group, requesterId);

        if (requesterId.equals(group.getOrganizerId())) {
            throw new ApiException("BAD_REQUEST", "Eres el organizador de este grupo", HttpStatus.BAD_REQUEST);
        }
        if (request == null || request.subject() == null || request.subject().isBlank()
                || request.message() == null || request.message().isBlank()) {
            throw new ApiException("BAD_REQUEST", "El asunto y el mensaje son obligatorios", HttpStatus.BAD_REQUEST);
        }

        MapItUser requester = userService.getByIdOrThrow(requesterId);
        MapItUser organizer = userService.getByIdOrThrow(group.getOrganizerId());

        notificationSender.sendGroupOrganizerNoticeEmail(
                organizer.getEmail(), organizer.getName(), group.getName(),
                requester.getName(), request.subject().trim(), request.message().trim());

        log.info("Aviso enviado al organizador groupId={} fromUserId={}", groupId, requesterId);
    }

    /**
     * Difunde un mensaje del organizador a (una parte o) todos los miembros del grupo. Solo el
     * organizador puede hacerlo.
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo
     * @param request     mensaje y destinatarios (vacío/{@code null} = todos los miembros)
     */
    public void contactMembers(String requesterId, String groupId, ContactMembersRequest request) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireOrganizer(group, requesterId);

        if (request == null || request.subject() == null || request.subject().isBlank()
                || request.message() == null || request.message().isBlank()) {
            throw new ApiException("BAD_REQUEST", "El asunto y el mensaje son obligatorios", HttpStatus.BAD_REQUEST);
        }

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId).stream()
                .filter(member -> !member.getUserId().equals(requesterId))
                .toList();

        List<String> recipientUserIds = request.recipientUserIds();
        if (recipientUserIds != null && !recipientUserIds.isEmpty()) {
            Set<String> requested = new LinkedHashSet<>(recipientUserIds);
            members = members.stream().filter(member -> requested.contains(member.getUserId())).toList();
        }

        if (members.isEmpty()) {
            throw new ApiException("BAD_REQUEST", "No hay destinatarios seleccionados", HttpStatus.BAD_REQUEST);
        }

        MapItUser organizer = userService.getByIdOrThrow(requesterId);
        String subject = request.subject().trim();
        String message = request.message().trim();

        members.forEach(member -> {
            MapItUser recipient = userService.getByIdOrThrow(member.getUserId());
            notificationSender.sendGroupBroadcastEmail(
                    recipient.getEmail(), recipient.getName(), group.getName(), organizer.getName(),
                    subject, message);
        });

        log.info("Mensaje difundido a miembros groupId={} organizerId={} destinatarios={}",
                groupId, requesterId, members.size());
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    /**
     * Valida y persiste una invitación nueva, y envía el email correspondiente.
     * Compartido por {@link #createGroup} (invitaciones iniciales) y {@link #inviteUser}.
     */
    private GroupInvitation sendInvitation(Group group, String requesterId, String invitedUserId) {
        if (invitedUserId.equals(requesterId)) {
            throw new ApiException("BAD_REQUEST", "No puedes invitarte a ti mismo", HttpStatus.BAD_REQUEST);
        }

        MapItUser invitedUser = userService.getByIdOrThrow(invitedUserId);

        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), invitedUserId)) {
            throw new ApiException("ALREADY_MEMBER", "Este usuario ya pertenece a este grupo", HttpStatus.CONFLICT);
        }
        if (groupInvitationRepository.existsByGroupIdAndInvitedUserIdAndStatus(
                group.getId(), invitedUserId, GroupInvitationStatus.PENDING)) {
            throw new ApiException("ALREADY_INVITED",
                    "Ya se ha invitado a este usuario", HttpStatus.CONFLICT);
        }

        GroupInvitation invitation = new GroupInvitation();
        invitation.setGroupId(group.getId());
        invitation.setInvitedUserId(invitedUserId);
        invitation.setInvitedByUserId(requesterId);
        invitation.setStatus(GroupInvitationStatus.PENDING);
        invitation.setCreatedAt(Instant.now());
        GroupInvitation saved = groupInvitationRepository.save(invitation);
        log.info("Invitación persistida invitationId={} groupId={} invitedUserId={} — enviando email...",
                saved.getId(), group.getId(), invitedUserId);

        MapItUser organizer = userService.getByIdOrThrow(requesterId);
        notificationSender.sendGroupInvitationEmail(
                invitedUser.getEmail(), invitedUser.getName(), group.getName(), organizer.getName(), saved.getId());
        log.info("notificationSender.sendGroupInvitationEmail() ha retornado sin excepción invitationId={}",
                saved.getId());

        return saved;
    }

    /**
     * Invita por email: si ya pertenece a un usuario registrado, delega en {@link #sendInvitation}
     * (misma dedupe de ya-miembro/ya-invitado); si no, persiste una invitación sin
     * {@code invitedUserId} y envía el correo de alta ({@code sendGroupSignupInvitationEmail}).
     * Compartido por {@link #createGroup} (invitaciones iniciales) e {@link #inviteByEmail}.
     */
    private GroupInvitation sendInvitationByEmail(Group group, String requesterId, String rawEmail) {
        String email = normalizeEmail(rawEmail);

        Optional<MapItUser> existingUser = userService.findByEmail(email);
        if (existingUser.isPresent()) {
            return sendInvitation(group, requesterId, existingUser.get().getId());
        }

        if (groupInvitationRepository.existsByGroupIdAndInvitedEmailAndStatus(
                group.getId(), email, GroupInvitationStatus.PENDING)) {
            throw new ApiException("ALREADY_INVITED",
                    "Ya se ha invitado a este email", HttpStatus.CONFLICT);
        }

        GroupInvitation invitation = new GroupInvitation();
        invitation.setGroupId(group.getId());
        invitation.setInvitedEmail(email);
        invitation.setInvitedByUserId(requesterId);
        invitation.setStatus(GroupInvitationStatus.PENDING);
        invitation.setCreatedAt(Instant.now());
        GroupInvitation saved = groupInvitationRepository.save(invitation);
        log.info("Invitación por email persistida invitationId={} groupId={} — enviando email de alta...",
                saved.getId(), group.getId());

        MapItUser organizer = userService.getByIdOrThrow(requesterId);
        notificationSender.sendGroupSignupInvitationEmail(email, group.getName(), organizer.getName());

        return saved;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private GroupResponse buildGroupResponse(Group group) {
        List<GroupMemberResponse> members = resolveMembers(group.getId());
        List<GroupResponse.PendingInvitee> pendingInvitees =
                groupInvitationRepository.findByGroupIdAndStatus(group.getId(), GroupInvitationStatus.PENDING)
                        .stream()
                        .map(inv -> {
                            if (inv.getInvitedUserId() == null) {
                                return new GroupResponse.PendingInvitee(null, null, null, inv.getInvitedEmail());
                            }
                            MapItUser u = userService.getByIdOrThrow(inv.getInvitedUserId());
                            return new GroupResponse.PendingInvitee(u.getId(), u.getName(), u.getNick(), null);
                        })
                        .toList();
        return groupMapper.toResponse(group, members, pendingInvitees);
    }

    private GroupInvitationResponse buildInvitationResponse(GroupInvitation invitation) {
        Group group = getGroupOrThrow(invitation.getGroupId());
        String invitedUserName = null;
        String invitedUserNick = null;
        if (invitation.getInvitedUserId() != null) {
            MapItUser invitedUser = userService.getByIdOrThrow(invitation.getInvitedUserId());
            invitedUserName = invitedUser.getName();
            invitedUserNick = invitedUser.getNick();
        }
        MapItUser invitedBy = userService.getByIdOrThrow(invitation.getInvitedByUserId());
        return groupInvitationMapper.toResponse(invitation, group,
                invitedUserName, invitedUserNick,
                invitedBy.getName(), resolveMembers(group.getId()));
    }

    private List<GroupMemberResponse> resolveMembers(String groupId) {
        return groupMemberRepository.findByGroupId(groupId).stream()
                .map(member -> groupMapper.toMemberResponse(member, userService.getByIdOrThrow(member.getUserId())))
                .toList();
    }

    /**
     * Devuelve las invitaciones pendientes de un grupo. Solo el organizador puede verlas.
     *
     * @param requesterId id del usuario autenticado
     * @param groupId     id del grupo
     * @return invitaciones en estado PENDING
     */
    @Transactional(readOnly = true)
    public List<GroupInvitationResponse> getGroupPendingInvitations(String requesterId, String groupId) {
        requireUserId(requesterId);
        Group group = getGroupOrThrow(groupId);
        requireOrganizer(group, requesterId);
        return groupInvitationRepository.findByGroupIdAndStatus(groupId, GroupInvitationStatus.PENDING)
                .stream()
                .map(this::buildInvitationResponse)
                .toList();
    }

    private Group getGroupOrThrow(String groupId) {
        if (groupId == null) {
            throw new ApiException("BAD_REQUEST", "ID de grupo requerido", HttpStatus.BAD_REQUEST);
        }
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Grupo no encontrado", HttpStatus.NOT_FOUND));
    }

    private GroupInvitation getInvitationOrThrow(String invitationId) {
        if (invitationId == null) {
            throw new ApiException("BAD_REQUEST", "ID de invitación requerido", HttpStatus.BAD_REQUEST);
        }
        return groupInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Invitación no encontrada", HttpStatus.NOT_FOUND));
    }

    private void requireOrganizer(Group group, String userId) {
        if (!group.getOrganizerId().equals(userId)) {
            throw new ApiException("FORBIDDEN", "Solo el organizador puede realizar esta acción", HttpStatus.FORBIDDEN);
        }
    }

    private void requireMember(Group group, String userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new ApiException("FORBIDDEN", "No perteneces a este grupo", HttpStatus.FORBIDDEN);
        }
    }

    private void requireInvitationOwner(GroupInvitation invitation, String userId) {
        if (!invitation.getInvitedUserId().equals(userId)) {
            throw new ApiException("FORBIDDEN", "Esta invitación no es tuya", HttpStatus.FORBIDDEN);
        }
    }

    private void requirePendingInvitation(GroupInvitation invitation) {
        if (invitation.getStatus() != GroupInvitationStatus.PENDING) {
            throw new ApiException("CONFLICT", "Esta invitación ya ha sido resuelta", HttpStatus.CONFLICT);
        }
    }

    private void validateCategoryExists(String categoryId) {
        if (categoryId == null || !mainCategoryRepository.existsById(categoryId)) {
            throw new ApiException("BAD_REQUEST", "categoryId inválido", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireUserId(String userId) {
        if (userId == null) {
            throw new ApiException("BAD_REQUEST", "ID de usuario requerido", HttpStatus.BAD_REQUEST);
        }
    }
}
