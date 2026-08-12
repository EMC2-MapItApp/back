package emc.mapIt.service;

import emc.mapIt.dto.ChangeVisibilityRequest;
import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationEnrollment;
import emc.mapIt.entity.PublicationVisibility;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserType;
import emc.mapIt.exception.ApiException;
import emc.mapIt.groups.GroupMembershipSummary;
import emc.mapIt.groups.GroupService;
import emc.mapIt.mapper.PublicationMapper;
import emc.mapIt.notifications.NotificationService;
import emc.mapIt.repository.LocationTypeRepository;
import emc.mapIt.repository.PublicationAccessRequestRepository;
import emc.mapIt.repository.PublicationEnrollmentRepository;
import emc.mapIt.repository.PublicationInvitationRepository;
import emc.mapIt.repository.PublicationRepository;
import emc.mapIt.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {

    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    @Mock private PublicationRepository publicationRepository;
    @Mock private PublicationEnrollmentRepository publicationEnrollmentRepository;
    @Mock private PublicationInvitationRepository publicationInvitationRepository;
    @Mock private PublicationAccessRequestRepository publicationAccessRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private LocationTypeRepository locationTypeRepository;
    @Mock private PublicationMapper publicationMapper;
    @Mock private GroupService groupService;
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;
    @Mock private PublicationInvitationDispatcher publicationInvitationDispatcher;

    private PublicationService publicationService;

    @BeforeEach
    void setUp() {
        publicationService = new PublicationService(
                publicationRepository, publicationEnrollmentRepository, publicationInvitationRepository,
                publicationAccessRequestRepository,
                userRepository, locationTypeRepository, publicationMapper, groupService,
                userService, notificationService, publicationInvitationDispatcher);
    }

    // ── deleteExpiredPublications ───────────────────────────────

    @Test
    void deleteExpiredPublications_conCaducadasHaceMasDeTresMeses_lasElimina() {
        Publication caducada = new Publication();
        caducada.setId("pub-1");
        caducada.setEndDate(ZonedDateTime.now(MADRID_ZONE).minusMonths(4));

        when(publicationRepository.findExpiredSince(any())).thenReturn(List.of(caducada));

        publicationService.deleteExpiredPublications();

        ArgumentCaptor<ZonedDateTime> cutoffCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(publicationRepository).findExpiredSince(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(ZonedDateTime.now(MADRID_ZONE).minusMonths(2));

        verify(publicationRepository).deleteAll(List.of(caducada));
    }

    @Test
    void deleteExpiredPublications_sinCaducadas_noBorraNada() {
        when(publicationRepository.findExpiredSince(any())).thenReturn(List.of());

        publicationService.deleteExpiredPublications();

        verify(publicationRepository, never()).deleteAll(any());
    }

    // ── createEvent (visibilidad) ────────────────────────────────

    private static final String AUTHOR_ID = "author-1";
    private static final String OTHER_USER_ID = "other-1";
    private static final String GROUP_ID = "group-1";
    private static final String PUB_ID = "pub-1";

    private User particularUser(String id) {
        User user = new User();
        user.setId(id);
        user.setUserType(UserType.PARTICULAR);
        return user;
    }

    private LocationType locationType(String id) {
        LocationType type = new LocationType();
        type.setId(id);
        type.setName("Quedadas");
        type.setDescription("desc");
        return type;
    }

    private CreatePublicationRequest privateRequest(String groupId) {
        return new CreatePublicationRequest(
                null, "loc-1", "Título", null,
                LocalDateTime.now(), null,
                BigDecimal.valueOf(40.0), BigDecimal.valueOf(-3.0), 0,
                Map.of(), PublicationVisibility.PRIVATE_GROUP, groupId, null);
    }

    @Test
    void createEvent_privadaSinGroupId_creaSinGrupoVinculado() {
        // groupId es opcional en PRIVATE_GROUP: "solo invitados", sin grupo vinculado — se podrá
        // asociar un grupo (o invitar) más adelante desde la edición.
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(locationTypeRepository.findById("loc-1")).thenReturn(Optional.of(locationType("loc-1")));

        Publication mapped = new Publication();
        mapped.setLocationTypeId("loc-1");
        mapped.setStartDate(ZonedDateTime.now(MADRID_ZONE));
        mapped.setEndDate(ZonedDateTime.now(MADRID_ZONE).plusHours(2));
        mapped.setVisibility(PublicationVisibility.PRIVATE_GROUP);
        mapped.setGroupId(null);

        when(publicationMapper.toEntity(any(CreatePublicationRequest.class), any(User.class), any()))
                .thenReturn(mapped);
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> {
            Publication p = inv.getArgument(0);
            p.setId(PUB_ID);
            return p;
        });
        when(groupService.getMembershipSummary(null, AUTHOR_ID))
                .thenReturn(new GroupMembershipSummary(null, null, 0, false, false, false));

        publicationService.createEvent(AUTHOR_ID, privateRequest(null));

        ArgumentCaptor<Publication> savedCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(publicationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getVisibility()).isEqualTo(PublicationVisibility.PRIVATE_GROUP);
        assertThat(savedCaptor.getValue().getGroupId()).isNull();
        verify(groupService, never()).isOrganizer(any(), any());
    }

    @Test
    void createEvent_privadaConGrupoQueNoOrganiza_lanzaForbidden() {
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(locationTypeRepository.findById("loc-1")).thenReturn(Optional.of(locationType("loc-1")));
        when(groupService.isOrganizer(GROUP_ID, AUTHOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> publicationService.createEvent(AUTHOR_ID, privateRequest(GROUP_ID)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");

        verify(publicationRepository, never()).save(any());
    }

    @Test
    void createEvent_privadaConGrupoPropio_creaConVisibilidadYGroupId() {
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(locationTypeRepository.findById("loc-1")).thenReturn(Optional.of(locationType("loc-1")));
        when(groupService.isOrganizer(GROUP_ID, AUTHOR_ID)).thenReturn(true);

        Publication mapped = new Publication();
        mapped.setLocationTypeId("loc-1");
        mapped.setStartDate(ZonedDateTime.now(MADRID_ZONE));
        mapped.setEndDate(ZonedDateTime.now(MADRID_ZONE).plusHours(2));
        mapped.setVisibility(PublicationVisibility.PRIVATE_GROUP);
        mapped.setGroupId(GROUP_ID);

        when(publicationMapper.toEntity(any(CreatePublicationRequest.class), any(User.class), any()))
                .thenReturn(mapped);
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> {
            Publication p = inv.getArgument(0);
            p.setId(PUB_ID);
            return p;
        });
        when(groupService.getMembershipSummary(GROUP_ID, AUTHOR_ID))
                .thenReturn(new GroupMembershipSummary(GROUP_ID, "Grupo", 1, true, true, false));

        publicationService.createEvent(AUTHOR_ID, privateRequest(GROUP_ID));

        ArgumentCaptor<Publication> savedCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(publicationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getVisibility()).isEqualTo(PublicationVisibility.PRIVATE_GROUP);
        assertThat(savedCaptor.getValue().getGroupId()).isEqualTo(GROUP_ID);

        verify(publicationMapper).toResponse(any(Publication.class), any(Long.class),
                argThat(summary -> summary != null && summary.isMember()));
    }

    // ── enroll (visibilidad) ──────────────────────────────────────

    private Publication privatePublication() {
        Publication publication = new Publication();
        publication.setId(PUB_ID);
        publication.setActive(true);
        publication.setVisibility(PublicationVisibility.PRIVATE_GROUP);
        publication.setGroupId(GROUP_ID);
        return publication;
    }

    @Test
    void enroll_publicacionPrivadaNoMiembro_lanzaForbidden() {
        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(groupService.isMember(GROUP_ID, OTHER_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> publicationService.enroll(PUB_ID, OTHER_USER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("NOT_GROUP_MEMBER");

        verify(publicationEnrollmentRepository, never()).save(any());
    }

    @Test
    void enroll_publicacionPrivadaMiembro_permiteInscripcion() {
        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(groupService.isMember(GROUP_ID, OTHER_USER_ID)).thenReturn(true);
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(particularUser(OTHER_USER_ID)));
        when(publicationEnrollmentRepository.existsByPublicationIdAndUserId(PUB_ID, OTHER_USER_ID)).thenReturn(false);
        when(publicationEnrollmentRepository.countByPublicationId(PUB_ID)).thenReturn(0L);

        publicationService.enroll(PUB_ID, OTHER_USER_ID);

        verify(publicationEnrollmentRepository).save(any(PublicationEnrollment.class));
    }

    // ── getEnrollments (visibilidad) ────────────────────────────────

    @Test
    void getEnrollments_publicacionPrivadaNoMiembro_lanzaForbidden() {
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(groupService.isMember(GROUP_ID, OTHER_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> publicationService.getEnrollments(PUB_ID, OTHER_USER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");
    }

    // ── changeVisibility ──────────────────────────────────────────

    @Test
    void changeVisibility_privadaAPublica_siemprePermitido() {
        Publication publication = privatePublication();
        publication.setAuthorId(AUTHOR_ID);

        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(publicationEnrollmentRepository.countByPublicationId(PUB_ID)).thenReturn(0L);

        publicationService.changeVisibility(PUB_ID, AUTHOR_ID,
                new ChangeVisibilityRequest(PublicationVisibility.PUBLIC, null));

        assertThat(publication.getVisibility()).isEqualTo(PublicationVisibility.PUBLIC);
        assertThat(publication.getGroupId()).isNull();
        verify(groupService, never()).isOrganizer(any(), any());
    }

    @Test
    void changeVisibility_publicaAPrivadaSinInscritosAjenos_permiteCambio() {
        Publication publication = new Publication();
        publication.setId(PUB_ID);
        publication.setAuthorId(AUTHOR_ID);
        publication.setVisibility(PublicationVisibility.PUBLIC);

        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(groupService.isOrganizer(GROUP_ID, AUTHOR_ID)).thenReturn(true);
        when(groupService.getMemberUserIds(GROUP_ID)).thenReturn(Set.of(AUTHOR_ID, OTHER_USER_ID));
        when(publicationEnrollmentRepository.findByPublicationId(PUB_ID)).thenReturn(List.of(
                new PublicationEnrollment(PUB_ID, OTHER_USER_ID, ZonedDateTime.now(MADRID_ZONE))));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(publicationEnrollmentRepository.countByPublicationId(PUB_ID)).thenReturn(1L);
        when(groupService.getMembershipSummary(GROUP_ID, AUTHOR_ID))
                .thenReturn(new GroupMembershipSummary(GROUP_ID, "Grupo", 2, true, true, false));

        publicationService.changeVisibility(PUB_ID, AUTHOR_ID,
                new ChangeVisibilityRequest(PublicationVisibility.PRIVATE_GROUP, GROUP_ID));

        assertThat(publication.getVisibility()).isEqualTo(PublicationVisibility.PRIVATE_GROUP);
        assertThat(publication.getGroupId()).isEqualTo(GROUP_ID);
    }

    @Test
    void changeVisibility_publicaAPrivadaConInscritosAjenos_lanzaConflict() {
        Publication publication = new Publication();
        publication.setId(PUB_ID);
        publication.setAuthorId(AUTHOR_ID);
        publication.setVisibility(PublicationVisibility.PUBLIC);

        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(groupService.isOrganizer(GROUP_ID, AUTHOR_ID)).thenReturn(true);
        when(groupService.getMemberUserIds(GROUP_ID)).thenReturn(Set.of(AUTHOR_ID));
        when(publicationEnrollmentRepository.findByPublicationId(PUB_ID)).thenReturn(List.of(
                new PublicationEnrollment(PUB_ID, OTHER_USER_ID, ZonedDateTime.now(MADRID_ZONE))));

        assertThatThrownBy(() -> publicationService.changeVisibility(PUB_ID, AUTHOR_ID,
                new ChangeVisibilityRequest(PublicationVisibility.PRIVATE_GROUP, GROUP_ID)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FOREIGN_ENROLLMENTS");

        verify(publicationRepository, never()).save(any());
    }

    @Test
    void changeVisibility_noAutorNiAdmin_lanzaForbidden() {
        Publication publication = new Publication();
        publication.setId(PUB_ID);
        publication.setAuthorId(AUTHOR_ID);
        publication.setVisibility(PublicationVisibility.PUBLIC);

        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(particularUser(OTHER_USER_ID)));

        assertThatThrownBy(() -> publicationService.changeVisibility(PUB_ID, OTHER_USER_ID,
                new ChangeVisibilityRequest(PublicationVisibility.PUBLIC, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");

        verify(publicationRepository, never()).save(any());
    }
}
