package emc.mapIt.groups;

import emc.mapIt.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST del dominio Grupos. Todas las rutas requieren sesión (no hay ninguna
 * declarada pública en {@code SecurityConfig}) — un usuario anónimo no puede ver grupos ni
 * invitaciones ajenas.
 */
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    private final GroupService groupService;
    private final AuthService authService;

    /**
     * Constructor para inyección de dependencias.
     */
    public GroupController(GroupService groupService, AuthService authService) {
        this.groupService = groupService;
        this.authService = authService;
    }

    /**
     * Crea un grupo nuevo, con el usuario autenticado como organizador.
     *
     * @param request       payload de creación
     * @param authorization cabecera Authorization con JWT
     * @return grupo creado
     */
    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de creación de grupo");
        GroupResponse response = groupService.createGroup(authService.requireUserId(authorization), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista los grupos del usuario autenticado (organizador o miembro).
     *
     * @param authorization cabecera Authorization con JWT
     * @return grupos a los que pertenece
     */
    @GetMapping("/mine")
    public List<GroupResponse> getMine(@RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de mis grupos");
        return groupService.getMyGroups(authService.requireUserId(authorization));
    }

    /**
     * Recupera el detalle de un grupo. Solo sus miembros pueden verlo.
     *
     * @param id            identificador del grupo
     * @param authorization cabecera Authorization con JWT
     * @return detalle del grupo
     */
    @GetMapping("/{id}")
    public GroupResponse getById(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de grupo id={}", id);
        return groupService.getGroupById(authService.requireUserId(authorization), id);
    }

    /**
     * Actualiza un grupo existente. Solo el organizador puede hacerlo.
     *
     * @param id            identificador del grupo
     * @param request       campos a actualizar
     * @param authorization cabecera Authorization con JWT
     * @return grupo actualizado
     */
    @PatchMapping("/{id}")
    public GroupResponse update(@PathVariable String id, @Valid @RequestBody UpdateGroupRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de actualización de grupo id={}", id);
        return groupService.updateGroup(authService.requireUserId(authorization), id, request);
    }

    /**
     * Invita a un usuario ya existente a un grupo. Solo el organizador puede invitar.
     *
     * @param id            identificador del grupo
     * @param request       id del usuario a invitar
     * @param authorization cabecera Authorization con JWT
     * @return invitación creada
     */
    @PostMapping("/{id}/invitations")
    public ResponseEntity<GroupInvitationResponse> invite(@PathVariable String id,
            @Valid @RequestBody InviteUserRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de invitación groupId={}", id);
        GroupInvitationResponse response = groupService.inviteUser(authService.requireUserId(authorization), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Invita por email a alguien que puede no estar registrado todavía. Solo el organizador
     * puede invitar.
     *
     * @param id            identificador del grupo
     * @param request       email a invitar
     * @param authorization cabecera Authorization con JWT
     * @return invitación creada
     */
    @PostMapping("/{id}/invitations/by-email")
    public ResponseEntity<GroupInvitationResponse> inviteByEmail(@PathVariable String id,
            @Valid @RequestBody InviteByEmailRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de invitación por email groupId={}", id);
        GroupInvitationResponse response = groupService.inviteByEmail(authService.requireUserId(authorization), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Cancela una invitación pendiente (la elimina). El organizador del grupo o el propio
     * invitado pueden cancelarla.
     *
     * @param invitationId  identificador de la invitación
     * @param authorization cabecera Authorization con JWT
     * @return sin contenido
     */
    @DeleteMapping("/invitations/{invitationId}/cancel")
    public ResponseEntity<Void> cancelInvitation(@PathVariable String invitationId,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de cancelación de invitación id={}", invitationId);
        groupService.cancelInvitation(authService.requireUserId(authorization), invitationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Expulsa a un miembro del grupo. Solo el organizador puede hacerlo.
     *
     * @param id            identificador del grupo
     * @param userId        identificador del miembro a expulsar
     * @param authorization cabecera Authorization con JWT
     * @return sin contenido
     */
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable String id, @PathVariable String userId,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de expulsión groupId={} targetUserId={}", id, userId);
        groupService.removeMember(authService.requireUserId(authorization), id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Devuelve las invitaciones pendientes de un grupo. Solo el organizador puede verlas.
     *
     * @param id            identificador del grupo
     * @param authorization cabecera Authorization con JWT
     * @return invitaciones en estado PENDING
     */
    @GetMapping("/{id}/invitations")
    public List<GroupInvitationResponse> getGroupPendingInvitations(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de invitaciones pendientes groupId={}", id);
        return groupService.getGroupPendingInvitations(authService.requireUserId(authorization), id);
    }

    /**
     * Elimina un grupo permanentemente. Solo el organizador puede hacerlo.
     * Borra en cascada miembros e invitaciones del grupo.
     *
     * @param id            identificador del grupo
     * @param authorization cabecera Authorization con JWT
     * @return sin contenido
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de eliminaci\u00f3n de grupo id={}", id);
        groupService.deleteGroup(authService.requireUserId(authorization), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Abandona un grupo del que se es miembro.
     *
     * @param id            identificador del grupo
     * @param authorization cabecera Authorization con JWT
     * @return sin contenido
     */
    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<Void> leave(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de abandono de grupo id={}", id);
        groupService.leaveGroup(authService.requireUserId(authorization), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Envía un aviso (email) al organizador del grupo.
     *
     * @param id            identificador del grupo
     * @param request       mensaje a enviar
     * @param authorization cabecera Authorization con JWT
     * @return sin contenido
     */
    @PostMapping("/{id}/notify-organizer")
    public ResponseEntity<Void> notifyOrganizer(@PathVariable String id,
            @Valid @RequestBody NotifyOrganizerRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de aviso al organizador groupId={}", id);
        groupService.notifyOrganizer(authService.requireUserId(authorization), id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lista las invitaciones pendientes del usuario autenticado.
     *
     * @param authorization cabecera Authorization con JWT
     * @return invitaciones en estado PENDING
     */
    @GetMapping("/invitations/pending")
    public List<GroupInvitationResponse> getPendingInvitations(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de invitaciones pendientes");
        return groupService.getPendingInvitations(authService.requireUserId(authorization));
    }

    /**
     * Recupera el detalle de una invitación (pantalla del enlace de correo). Solo el usuario
     * invitado puede verla.
     *
     * @param id            identificador de la invitación
     * @param authorization cabecera Authorization con JWT
     * @return detalle de la invitación
     */
    @GetMapping("/invitations/{id}")
    public GroupInvitationResponse getInvitationById(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.debug("Lectura de invitación id={}", id);
        return groupService.getInvitationById(authService.requireUserId(authorization), id);
    }

    /**
     * Acepta una invitación pendiente.
     *
     * @param id            identificador de la invitación
     * @param authorization cabecera Authorization con JWT
     * @return grupo al que se ha unido
     */
    @PostMapping("/invitations/{id}/accept")
    public GroupResponse acceptInvitation(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de aceptación de invitación id={}", id);
        return groupService.acceptInvitation(authService.requireUserId(authorization), id);
    }

    /**
     * Rechaza una invitación pendiente.
     *
     * @param id            identificador de la invitación
     * @param authorization cabecera Authorization con JWT
     * @return sin contenido
     */
    @PostMapping("/invitations/{id}/decline")
    public ResponseEntity<Void> declineInvitation(@PathVariable String id,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        log.info("Solicitud de rechazo de invitación id={}", id);
        groupService.declineInvitation(authService.requireUserId(authorization), id);
        return ResponseEntity.noContent().build();
    }
}
