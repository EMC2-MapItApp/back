package emc.mapIt.groups;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.notifications.NotificationSender;
import emc.mapIt.repository.MainCategoryRepository;
import emc.mapIt.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupInvitationRepository groupInvitationRepository;
    @Mock private MainCategoryRepository mainCategoryRepository;
    @Mock private UserService userService;
    @Mock private NotificationSender notificationSender;

    // Mappers reales (sin dependencias propias) en vez de mocks, igual que PublicationMapper
    // en PublicationServiceTest sería tratado si existiera — su lógica es trivial y probarla
    // vía el servicio da más confianza que stubearla.
    private final GroupMapper groupMapper = new GroupMapper();
    private final GroupInvitationMapper groupInvitationMapper = new GroupInvitationMapper();

    private GroupService groupService;

    private static final String ORGANIZER_ID = "org-1";
    private static final String MEMBER_ID = "mem-1";
    private static final String GROUP_ID = "g-1";
    private static final String CATEGORY_ID = "cat-1";

    private Group grupo;
    private MapItUser organizador;
    private MapItUser miembro;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupRepository, groupMemberRepository, groupInvitationRepository,
                mainCategoryRepository, userService, notificationSender,
                groupMapper, groupInvitationMapper);

        grupo = new Group();
        grupo.setId(GROUP_ID);
        grupo.setName("Club de Ciclismo");
        grupo.setDescription("Rutas los fines de semana");
        grupo.setCategoryId(CATEGORY_ID);
        grupo.setOrganizerId(ORGANIZER_ID);
        grupo.setCreatedAt(Instant.now());

        organizador = new MapItUser(ORGANIZER_ID, "Organizador", "org@test.com", "hash", UserType.PARTICULAR);
        organizador.setNick("organizador");

        miembro = new MapItUser(MEMBER_ID, "Miembro", "miembro@test.com", "hash", UserType.PARTICULAR);
        miembro.setNick("miembro");
    }

    // ── createGroup ──────────────────────────────────────────────

    @Test
    void createGroup_conDatosValidos_creaGrupoConOrganizadorComoMiembro() {
        CreateGroupRequest request = new CreateGroupRequest("Club", "Descripción", CATEGORY_ID, null);

        when(userService.getByIdOrThrow(ORGANIZER_ID)).thenReturn(organizador);
        when(mainCategoryRepository.existsById(CATEGORY_ID)).thenReturn(true);
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            g.setId(GROUP_ID);
            return g;
        });
        when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        GroupResponse response = groupService.createGroup(ORGANIZER_ID, request);

        assertThat(response.id()).isEqualTo(GROUP_ID);
        assertThat(response.organizerId()).isEqualTo(ORGANIZER_ID);

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(ORGANIZER_ID);
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(GroupRole.ORGANIZER);
    }

    @Test
    void createGroup_conCategoriaInexistente_lanzaApiException() {
        CreateGroupRequest request = new CreateGroupRequest("Club", "Descripción", "cat-invalida", null);

        when(userService.getByIdOrThrow(ORGANIZER_ID)).thenReturn(organizador);
        when(mainCategoryRepository.existsById("cat-invalida")).thenReturn(false);

        assertThatThrownBy(() -> groupService.createGroup(ORGANIZER_ID, request))
                .isInstanceOf(ApiException.class);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void createGroup_conRequestNull_lanzaApiException() {
        assertThatThrownBy(() -> groupService.createGroup(ORGANIZER_ID, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createGroup_conInviteUserIds_creaInvitacionesYEnviaEmails() {
        CreateGroupRequest request = new CreateGroupRequest(
                "Club", "Descripción", CATEGORY_ID, List.of(MEMBER_ID, ORGANIZER_ID));

        when(userService.getByIdOrThrow(ORGANIZER_ID)).thenReturn(organizador);
        when(userService.getByIdOrThrow(MEMBER_ID)).thenReturn(miembro);
        when(mainCategoryRepository.existsById(CATEGORY_ID)).thenReturn(true);
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            g.setId(GROUP_ID);
            return g;
        });
        when(groupInvitationRepository.save(any(GroupInvitation.class))).thenAnswer(inv -> {
            GroupInvitation gi = inv.getArgument(0);
            gi.setId("inv-1");
            return gi;
        });
        when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        groupService.createGroup(ORGANIZER_ID, request);

        // El propio organizador aparece en inviteUserIds pero se ignora (no se autoinvita).
        verify(groupInvitationRepository, times(1)).save(any(GroupInvitation.class));
        verify(notificationSender).sendGroupInvitationEmail(
                eq("miembro@test.com"), eq("Miembro"), eq("Club"), eq("Organizador"), eq("inv-1"));
    }

    // ── updateGroup ──────────────────────────────────────────────

    @Test
    void updateGroup_comoOrganizador_actualizaCampos() {
        UpdateGroupRequest request = new UpdateGroupRequest("Nuevo nombre", null, null);

        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        GroupResponse response = groupService.updateGroup(ORGANIZER_ID, GROUP_ID, request);

        assertThat(response.name()).isEqualTo("Nuevo nombre");
    }

    @Test
    void updateGroup_comoNoOrganizador_lanzaForbidden() {
        UpdateGroupRequest request = new UpdateGroupRequest("Nuevo nombre", null, null);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));

        assertThatThrownBy(() -> groupService.updateGroup(MEMBER_ID, GROUP_ID, request))
                .isInstanceOf(ApiException.class);

        verify(groupRepository, never()).save(any());
    }

    @Test
    void updateGroup_conGrupoInexistente_lanzaNotFound() {
        when(groupRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.updateGroup(ORGANIZER_ID, "no-existe",
                new UpdateGroupRequest("x", null, null)))
                .isInstanceOf(ApiException.class);
    }

    // ── getGroupById ─────────────────────────────────────────────

    @Test
    void getGroupById_comoMiembro_devuelveGrupo() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(true);
        when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        GroupResponse response = groupService.getGroupById(MEMBER_ID, GROUP_ID);

        assertThat(response.id()).isEqualTo(GROUP_ID);
    }

    @Test
    void getGroupById_comoNoMiembro_lanzaForbidden() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, "extraño")).thenReturn(false);

        assertThatThrownBy(() -> groupService.getGroupById("extraño", GROUP_ID))
                .isInstanceOf(ApiException.class);
    }

    // ── getMyGroups ──────────────────────────────────────────────

    @Test
    void getMyGroups_devuelveGruposDeLasMembresias() {
        GroupMember membership = new GroupMember();
        membership.setGroupId(GROUP_ID);
        membership.setUserId(MEMBER_ID);
        membership.setRole(GroupRole.MEMBER);

        when(groupMemberRepository.findByUserId(MEMBER_ID)).thenReturn(List.of(membership));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        List<GroupResponse> response = groupService.getMyGroups(MEMBER_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(GROUP_ID);
    }

    // ── inviteUser ───────────────────────────────────────────────

    @Test
    void inviteUser_comoOrganizador_creaInvitacionYEnviaEmail() {
        InviteUserRequest request = new InviteUserRequest(MEMBER_ID);

        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(userService.getByIdOrThrow(MEMBER_ID)).thenReturn(miembro);
        when(userService.getByIdOrThrow(ORGANIZER_ID)).thenReturn(organizador);
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(false);
        when(groupInvitationRepository.existsByGroupIdAndInvitedUserIdAndStatus(
                GROUP_ID, MEMBER_ID, GroupInvitationStatus.PENDING)).thenReturn(false);
        when(groupInvitationRepository.save(any(GroupInvitation.class))).thenAnswer(inv -> {
            GroupInvitation gi = inv.getArgument(0);
            gi.setId("inv-1");
            return gi;
        });
        when(groupMemberRepository.countByGroupId(GROUP_ID)).thenReturn(1L);

        GroupInvitationResponse response = groupService.inviteUser(ORGANIZER_ID, GROUP_ID, request);

        assertThat(response.invitedUserId()).isEqualTo(MEMBER_ID);
        assertThat(response.status()).isEqualTo(GroupInvitationStatus.PENDING);
        verify(notificationSender).sendGroupInvitationEmail(
                eq("miembro@test.com"), eq("Miembro"), eq("Club de Ciclismo"), eq("Organizador"), eq("inv-1"));
    }

    @Test
    void inviteUser_comoNoOrganizador_lanzaForbidden() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));

        assertThatThrownBy(() -> groupService.inviteUser(MEMBER_ID, GROUP_ID, new InviteUserRequest("otro-id")))
                .isInstanceOf(ApiException.class);

        verify(groupInvitationRepository, never()).save(any());
    }

    @Test
    void inviteUser_aUsuarioYaMiembro_lanzaConflict() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(userService.getByIdOrThrow(MEMBER_ID)).thenReturn(miembro);
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> groupService.inviteUser(ORGANIZER_ID, GROUP_ID, new InviteUserRequest(MEMBER_ID)))
                .isInstanceOf(ApiException.class);

        verify(groupInvitationRepository, never()).save(any());
    }

    @Test
    void inviteUser_conInvitacionPendienteExistente_lanzaConflict() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(userService.getByIdOrThrow(MEMBER_ID)).thenReturn(miembro);
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(false);
        when(groupInvitationRepository.existsByGroupIdAndInvitedUserIdAndStatus(
                GROUP_ID, MEMBER_ID, GroupInvitationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> groupService.inviteUser(ORGANIZER_ID, GROUP_ID, new InviteUserRequest(MEMBER_ID)))
                .isInstanceOf(ApiException.class);

        verify(groupInvitationRepository, never()).save(any());
    }

    @Test
    void inviteUser_aSiMismo_lanzaBadRequest() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));

        assertThatThrownBy(() -> groupService.inviteUser(ORGANIZER_ID, GROUP_ID, new InviteUserRequest(ORGANIZER_ID)))
                .isInstanceOf(ApiException.class);

        verify(groupInvitationRepository, never()).save(any());
    }

    // ── getPendingInvitations ────────────────────────────────────

    @Test
    void getPendingInvitations_devuelveInvitacionesPendientes() {
        GroupInvitation invitation = invitacionPendiente();

        when(groupInvitationRepository.findByInvitedUserIdAndStatus(MEMBER_ID, GroupInvitationStatus.PENDING))
                .thenReturn(List.of(invitation));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(userService.getByIdOrThrow(ORGANIZER_ID)).thenReturn(organizador);
        when(groupMemberRepository.countByGroupId(GROUP_ID)).thenReturn(1L);

        List<GroupInvitationResponse> response = groupService.getPendingInvitations(MEMBER_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).invitedByName()).isEqualTo("Organizador");
    }

    // ── acceptInvitation ─────────────────────────────────────────

    @Test
    void acceptInvitation_conInvitacionPropiaYPendiente_aceptaYAgregaMiembro() {
        GroupInvitation invitation = invitacionPendiente();

        when(groupInvitationRepository.findById("inv-1")).thenReturn(Optional.of(invitation));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(false);
        when(groupMemberRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        GroupResponse response = groupService.acceptInvitation(MEMBER_ID, "inv-1");

        assertThat(response.id()).isEqualTo(GROUP_ID);

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(GroupRole.MEMBER);

        ArgumentCaptor<GroupInvitation> invitationCaptor = ArgumentCaptor.forClass(GroupInvitation.class);
        verify(groupInvitationRepository).save(invitationCaptor.capture());
        assertThat(invitationCaptor.getValue().getStatus()).isEqualTo(GroupInvitationStatus.ACCEPTED);
    }

    @Test
    void acceptInvitation_deOtroUsuario_lanzaForbidden() {
        GroupInvitation invitation = invitacionPendiente();
        when(groupInvitationRepository.findById("inv-1")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> groupService.acceptInvitation("otro-usuario", "inv-1"))
                .isInstanceOf(ApiException.class);

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void acceptInvitation_yaResuelta_lanzaConflict() {
        GroupInvitation invitation = invitacionPendiente();
        invitation.setStatus(GroupInvitationStatus.ACCEPTED);
        when(groupInvitationRepository.findById("inv-1")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> groupService.acceptInvitation(MEMBER_ID, "inv-1"))
                .isInstanceOf(ApiException.class);
    }

    // ── declineInvitation ────────────────────────────────────────

    @Test
    void declineInvitation_conInvitacionPropiaYPendiente_marcaRechazada() {
        GroupInvitation invitation = invitacionPendiente();
        when(groupInvitationRepository.findById("inv-1")).thenReturn(Optional.of(invitation));

        groupService.declineInvitation(MEMBER_ID, "inv-1");

        ArgumentCaptor<GroupInvitation> captor = ArgumentCaptor.forClass(GroupInvitation.class);
        verify(groupInvitationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GroupInvitationStatus.DECLINED);

        verify(groupMemberRepository, never()).save(any());
    }

    // ── leaveGroup ───────────────────────────────────────────────

    @Test
    void leaveGroup_comoMiembro_eliminaLaMembresia() {
        GroupMember membership = new GroupMember();
        membership.setGroupId(GROUP_ID);
        membership.setUserId(MEMBER_ID);
        membership.setRole(GroupRole.MEMBER);

        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(Optional.of(membership));

        groupService.leaveGroup(MEMBER_ID, GROUP_ID);

        verify(groupMemberRepository).delete(membership);
    }

    @Test
    void leaveGroup_comoOrganizador_lanzaForbidden() {
        GroupMember membership = new GroupMember();
        membership.setGroupId(GROUP_ID);
        membership.setUserId(ORGANIZER_ID);
        membership.setRole(GroupRole.ORGANIZER);

        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, ORGANIZER_ID)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> groupService.leaveGroup(ORGANIZER_ID, GROUP_ID))
                .isInstanceOf(ApiException.class);

        verify(groupMemberRepository, never()).delete(any(GroupMember.class));
    }

    @Test
    void leaveGroup_sinPertenecer_lanzaNotFound() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.findByGroupIdAndUserId(GROUP_ID, "extraño")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.leaveGroup("extraño", GROUP_ID))
                .isInstanceOf(ApiException.class);
    }

    // ── notifyOrganizer ──────────────────────────────────────────

    @Test
    void notifyOrganizer_comoMiembro_enviaEmailAlOrganizador() {
        NotifyOrganizerRequest request = new NotifyOrganizerRequest("¿Cambiamos la hora de la próxima ruta?");

        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(true);
        when(userService.getByIdOrThrow(MEMBER_ID)).thenReturn(miembro);
        when(userService.getByIdOrThrow(ORGANIZER_ID)).thenReturn(organizador);

        groupService.notifyOrganizer(MEMBER_ID, GROUP_ID, request);

        verify(notificationSender).sendGroupOrganizerNoticeEmail(
                eq("org@test.com"), eq("Organizador"), eq("Club de Ciclismo"), eq("Miembro"), anyString());
    }

    @Test
    void notifyOrganizer_comoOrganizador_lanzaBadRequest() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, ORGANIZER_ID)).thenReturn(true);

        assertThatThrownBy(() -> groupService.notifyOrganizer(
                ORGANIZER_ID, GROUP_ID, new NotifyOrganizerRequest("mensaje")))
                .isInstanceOf(ApiException.class);

        verify(notificationSender, never()).sendGroupOrganizerNoticeEmail(any(), any(), any(), any(), any());
    }

    @Test
    void notifyOrganizer_comoNoMiembro_lanzaForbidden() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(grupo));
        when(groupMemberRepository.existsByGroupIdAndUserId(GROUP_ID, "extraño")).thenReturn(false);

        assertThatThrownBy(() -> groupService.notifyOrganizer(
                "extraño", GROUP_ID, new NotifyOrganizerRequest("mensaje")))
                .isInstanceOf(ApiException.class);
    }

    // ── Fixtures ─────────────────────────────────────────────────

    private GroupInvitation invitacionPendiente() {
        GroupInvitation invitation = new GroupInvitation();
        invitation.setId("inv-1");
        invitation.setGroupId(GROUP_ID);
        invitation.setInvitedUserId(MEMBER_ID);
        invitation.setInvitedByUserId(ORGANIZER_ID);
        invitation.setStatus(GroupInvitationStatus.PENDING);
        invitation.setCreatedAt(Instant.now());
        return invitation;
    }
}
