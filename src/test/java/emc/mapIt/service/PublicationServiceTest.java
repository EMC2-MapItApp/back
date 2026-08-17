package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.dto.ChangeVisibilityRequest;
import emc.mapIt.dto.CreatePublicationRequest;
import emc.mapIt.dto.PublicationAccessRequestResponse;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;
    @Mock private PublicationInvitationDispatcher publicationInvitationDispatcher;

    private PublicationService publicationService;

    private static final String AUTHOR_ID = "author-1";
    private static final String OTHER_USER_ID = "other-1";
    private static final String REQUESTER_ID = "requester-1";
    private static final String PUB_ID = "pub-1";

    @BeforeEach
    void setUp() {
        publicationService = new PublicationService(
                publicationRepository, publicationEnrollmentRepository, publicationInvitationRepository,
                publicationAccessRequestRepository,
                userRepository, locationTypeRepository, publicationMapper,
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

    // ── Fixtures ────────────────────────────────────────────────

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

    private CreatePublicationRequest privateRequest(List<String> inviteUserIds) {
        return new CreatePublicationRequest(
                null, "loc-1", "Título", null,
                LocalDateTime.now(), null,
                BigDecimal.valueOf(40.0), BigDecimal.valueOf(-3.0), 0,
                Map.of(), PublicationVisibility.PRIVATE, inviteUserIds);
    }

    private Publication privatePublication() {
        Publication publication = new Publication();
        publication.setId(PUB_ID);
        publication.setActive(true);
        publication.setAuthorId(AUTHOR_ID);
        publication.setVisibility(PublicationVisibility.PRIVATE);
        return publication;
    }

    // ── createEvent (visibilidad) ────────────────────────────────

    @Test
    void createEvent_privada_seCreaSinRequisitosDeGrupo() {
        // El modelo ya no ata la privacidad de una publicación a ningún grupo — cualquier
        // usuario puede crear una publicación privada sin organizar nada.
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(locationTypeRepository.findById("loc-1")).thenReturn(Optional.of(locationType("loc-1")));

        Publication mapped = new Publication();
        mapped.setLocationTypeId("loc-1");
        mapped.setStartDate(ZonedDateTime.now(MADRID_ZONE));
        mapped.setEndDate(ZonedDateTime.now(MADRID_ZONE).plusHours(2));
        mapped.setVisibility(PublicationVisibility.PRIVATE);

        when(publicationMapper.toEntity(any(CreatePublicationRequest.class), any(User.class), any()))
                .thenReturn(mapped);
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> {
            Publication p = inv.getArgument(0);
            p.setId(PUB_ID);
            return p;
        });

        publicationService.createEvent(AUTHOR_ID, privateRequest(null));

        ArgumentCaptor<Publication> savedCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(publicationRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getVisibility()).isEqualTo(PublicationVisibility.PRIVATE);
        // El autor siempre tiene acceso completo a su propia publicación, sin enmascarado.
        verify(publicationMapper).toResponse(any(Publication.class), eq(1L), eq(true), isNull());
        verify(publicationInvitationDispatcher, never()).dispatchAsync(any(), any(), any());
    }

    @Test
    void createEvent_privadaConInviteUserIds_creaInvitaciones() {
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(locationTypeRepository.findById("loc-1")).thenReturn(Optional.of(locationType("loc-1")));

        Publication mapped = new Publication();
        mapped.setLocationTypeId("loc-1");
        mapped.setStartDate(ZonedDateTime.now(MADRID_ZONE));
        mapped.setEndDate(ZonedDateTime.now(MADRID_ZONE).plusHours(2));
        mapped.setVisibility(PublicationVisibility.PRIVATE);

        when(publicationMapper.toEntity(any(CreatePublicationRequest.class), any(User.class), any()))
                .thenReturn(mapped);
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> {
            Publication p = inv.getArgument(0);
            p.setId(PUB_ID);
            return p;
        });

        publicationService.createEvent(AUTHOR_ID, privateRequest(List.of(OTHER_USER_ID)));

        verify(publicationInvitationDispatcher)
                .dispatchAsync(any(Publication.class), eq(AUTHOR_ID), eq(Set.of(OTHER_USER_ID)));
    }

    // ── enroll (visibilidad) ──────────────────────────────────────

    @Test
    void enroll_publicacionPrivadaSinInvitacion_lanzaForbidden() {
        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(particularUser(OTHER_USER_ID)));
        when(publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                PUB_ID, OTHER_USER_ID, PublicationInvitationStatus.DECLINED)).thenReturn(false);

        assertThatThrownBy(() -> publicationService.enroll(PUB_ID, OTHER_USER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("NO_ACCESS");

        verify(publicationEnrollmentRepository, never()).save(any());
    }

    @Test
    void enroll_publicacionPrivadaConInvitacion_permiteInscripcion() {
        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(particularUser(OTHER_USER_ID)));
        when(publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                PUB_ID, OTHER_USER_ID, PublicationInvitationStatus.DECLINED)).thenReturn(true);
        when(publicationEnrollmentRepository.existsByPublicationIdAndUserId(PUB_ID, OTHER_USER_ID)).thenReturn(false);
        when(publicationEnrollmentRepository.countByPublicationId(PUB_ID)).thenReturn(0L);

        publicationService.enroll(PUB_ID, OTHER_USER_ID);

        verify(publicationEnrollmentRepository).save(any(PublicationEnrollment.class));
    }

    // ── getEnrollments (visibilidad) ────────────────────────────────

    @Test
    void getEnrollments_publicacionPrivadaSinAcceso_lanzaForbidden() {
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(particularUser(OTHER_USER_ID)));
        when(publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                PUB_ID, OTHER_USER_ID, PublicationInvitationStatus.DECLINED)).thenReturn(false);

        assertThatThrownBy(() -> publicationService.getEnrollments(PUB_ID, OTHER_USER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");
    }

    // ── changeVisibility ──────────────────────────────────────────

    @Test
    void changeVisibility_privadaAPublica_siemprePermitido() {
        Publication publication = privatePublication();

        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        publicationService.changeVisibility(PUB_ID, AUTHOR_ID,
                new ChangeVisibilityRequest(PublicationVisibility.PUBLIC));

        assertThat(publication.getVisibility()).isEqualTo(PublicationVisibility.PUBLIC);
    }

    @Test
    void changeVisibility_publicaAPrivadaSinInscritosSinAcceso_permiteCambio() {
        Publication publication = new Publication();
        publication.setId(PUB_ID);
        publication.setAuthorId(AUTHOR_ID);
        publication.setVisibility(PublicationVisibility.PUBLIC);

        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(publicationEnrollmentRepository.findByPublicationId(PUB_ID)).thenReturn(List.of());
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));

        publicationService.changeVisibility(PUB_ID, AUTHOR_ID,
                new ChangeVisibilityRequest(PublicationVisibility.PRIVATE));

        assertThat(publication.getVisibility()).isEqualTo(PublicationVisibility.PRIVATE);
    }

    @Test
    void changeVisibility_publicaAPrivadaConInscritoSinAcceso_lanzaConflict() {
        Publication publication = new Publication();
        publication.setId(PUB_ID);
        publication.setAuthorId(AUTHOR_ID);
        publication.setVisibility(PublicationVisibility.PUBLIC);

        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(publicationEnrollmentRepository.findByPublicationId(PUB_ID)).thenReturn(List.of(
                new PublicationEnrollment(PUB_ID, OTHER_USER_ID, ZonedDateTime.now(MADRID_ZONE))));
        when(publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                PUB_ID, OTHER_USER_ID, PublicationInvitationStatus.DECLINED)).thenReturn(false);

        assertThatThrownBy(() -> publicationService.changeVisibility(PUB_ID, AUTHOR_ID,
                new ChangeVisibilityRequest(PublicationVisibility.PRIVATE)))
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
                new ChangeVisibilityRequest(PublicationVisibility.PUBLIC)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");

        verify(publicationRepository, never()).save(any());
    }

    // ── requestAccess ────────────────────────────────────────────

    @Test
    void requestAccess_publicacionPublica_lanzaBadRequest() {
        Publication publication = new Publication();
        publication.setId(PUB_ID);
        publication.setAuthorId(AUTHOR_ID);
        publication.setVisibility(PublicationVisibility.PUBLIC);
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));

        assertThatThrownBy(() -> publicationService.requestAccess(PUB_ID, REQUESTER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("BAD_REQUEST");
    }

    @Test
    void requestAccess_yaTieneAcceso_lanzaConflict() {
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(particularUser(REQUESTER_ID)));
        when(publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                PUB_ID, REQUESTER_ID, PublicationInvitationStatus.DECLINED)).thenReturn(true);

        assertThatThrownBy(() -> publicationService.requestAccess(PUB_ID, REQUESTER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("ALREADY_HAS_ACCESS");
    }

    @Test
    void requestAccess_solicitudPendienteExistente_lanzaConflict() {
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(particularUser(REQUESTER_ID)));
        when(publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                PUB_ID, REQUESTER_ID, PublicationInvitationStatus.DECLINED)).thenReturn(false);
        when(publicationAccessRequestRepository.existsByPublicationIdAndRequestedByUserIdAndStatus(
                PUB_ID, REQUESTER_ID, PublicationAccessRequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> publicationService.requestAccess(PUB_ID, REQUESTER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("ALREADY_REQUESTED");
    }

    @Test
    void requestAccess_datosValidos_creaSolicitudPendiente() {
        MapItUser requester = new MapItUser(REQUESTER_ID, "Solicitante", "req@test.com", "hash", UserType.PARTICULAR);
        requester.setNick("solicitante");
        MapItUser author = new MapItUser(AUTHOR_ID, "Autor", "autor@test.com", "hash", UserType.PARTICULAR);
        author.setNick("autor");

        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(particularUser(REQUESTER_ID)));
        when(publicationInvitationRepository.existsByPublicationIdAndInvitedUserIdAndStatusNot(
                PUB_ID, REQUESTER_ID, PublicationInvitationStatus.DECLINED)).thenReturn(false);
        when(publicationAccessRequestRepository.existsByPublicationIdAndRequestedByUserIdAndStatus(
                PUB_ID, REQUESTER_ID, PublicationAccessRequestStatus.PENDING)).thenReturn(false);
        when(userService.getByIdOrThrow(REQUESTER_ID)).thenReturn(requester);
        when(userService.getByIdOrThrow(AUTHOR_ID)).thenReturn(author);
        when(publicationAccessRequestRepository.save(any(PublicationAccessRequest.class))).thenAnswer(inv -> {
            PublicationAccessRequest r = inv.getArgument(0);
            r.setId("req-1");
            return r;
        });

        PublicationAccessRequestResponse response = publicationService.requestAccess(PUB_ID, REQUESTER_ID);

        assertThat(response.status()).isEqualTo(PublicationAccessRequestStatus.PENDING);
        verify(notificationService).notifyPublicationAccessRequest(eq(author), any(), eq(requester));
    }

    // ── acceptAccessRequest / rejectAccessRequest ───────────────────

    private PublicationAccessRequest solicitudPendiente() {
        PublicationAccessRequest request = new PublicationAccessRequest();
        request.setId("req-1");
        request.setPublicationId(PUB_ID);
        request.setRequestedByUserId(REQUESTER_ID);
        request.setStatus(PublicationAccessRequestStatus.PENDING);
        return request;
    }

    @Test
    void acceptAccessRequest_autor_creaInvitacionYPermiteEnroll() {
        Publication publication = privatePublication();
        PublicationAccessRequest request = solicitudPendiente();
        MapItUser requester = new MapItUser(REQUESTER_ID, "Solicitante", "req@test.com", "hash", UserType.PARTICULAR);

        when(publicationAccessRequestRepository.findById("req-1")).thenReturn(Optional.of(request));
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(publicationAccessRequestRepository.save(any(PublicationAccessRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userService.getByIdOrThrow(REQUESTER_ID)).thenReturn(requester);

        publicationService.acceptAccessRequest("req-1", AUTHOR_ID);

        ArgumentCaptor<PublicationInvitation> invitationCaptor = ArgumentCaptor.forClass(PublicationInvitation.class);
        verify(publicationInvitationRepository).save(invitationCaptor.capture());
        assertThat(invitationCaptor.getValue().getInvitedUserId()).isEqualTo(REQUESTER_ID);
        assertThat(invitationCaptor.getValue().getStatus()).isEqualTo(PublicationInvitationStatus.ACCEPTED);

        verify(notificationService).notifyPublicationAccessRequestResolved(eq(requester), any(), eq(true));
    }

    @Test
    void rejectAccessRequest_autor_marcaRechazada() {
        Publication publication = privatePublication();
        PublicationAccessRequest request = solicitudPendiente();
        MapItUser requester = new MapItUser(REQUESTER_ID, "Solicitante", "req@test.com", "hash", UserType.PARTICULAR);

        when(publicationAccessRequestRepository.findById("req-1")).thenReturn(Optional.of(request));
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(publication));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(particularUser(AUTHOR_ID)));
        when(publicationAccessRequestRepository.save(any(PublicationAccessRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userService.getByIdOrThrow(REQUESTER_ID)).thenReturn(requester);

        publicationService.rejectAccessRequest("req-1", AUTHOR_ID);

        ArgumentCaptor<PublicationAccessRequest> requestCaptor = ArgumentCaptor.forClass(PublicationAccessRequest.class);
        verify(publicationAccessRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(PublicationAccessRequestStatus.REJECTED);

        verify(publicationInvitationRepository, never()).save(any());
        verify(notificationService).notifyPublicationAccessRequestResolved(eq(requester), any(), eq(false));
    }

    // ── findAll / findById (anónimos) ───────────────────────────────

    @Test
    void findAll_anonimo_excluyePrivadas() {
        Publication publicaPub = new Publication();
        publicaPub.setId("pub-public");
        publicaPub.setVisibility(PublicationVisibility.PUBLIC);
        Publication privadaPub = privatePublication();

        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findByActiveTrueOrderByStartDateDesc())
                .thenReturn(List.of(publicaPub, privadaPub));

        publicationService.findAll(true, null);

        verify(publicationMapper, times(1)).toResponse(any(), anyLong(), any(Boolean.class), any());
        verify(publicationMapper).toResponse(eq(publicaPub), anyLong(), eq(true), isNull());
    }

    @Test
    void findById_anonimoPublicacionPrivada_lanzaNotFound() {
        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findById(PUB_ID)).thenReturn(Optional.of(privatePublication()));

        assertThatThrownBy(() -> publicationService.findById(PUB_ID, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void findByAuthor_anonimo_excluyePrivadas() {
        Publication publicaPub = new Publication();
        publicaPub.setId("pub-public");
        publicaPub.setAuthorId(AUTHOR_ID);
        publicaPub.setVisibility(PublicationVisibility.PUBLIC);
        Publication privadaPub = privatePublication();

        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findByAuthorIdAndActiveTrue(AUTHOR_ID))
                .thenReturn(List.of(publicaPub, privadaPub));

        publicationService.findByAuthor(AUTHOR_ID, true, null);

        verify(publicationMapper, times(1)).toResponse(any(), anyLong(), any(Boolean.class), any());
        verify(publicationMapper).toResponse(eq(publicaPub), anyLong(), eq(true), isNull());
    }

    @Test
    void findByAuthor_terceroAutenticadoSinAcceso_noExcluyePeroEnmascara() {
        Publication privadaPub = privatePublication();

        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findByAuthorIdAndActiveTrue(AUTHOR_ID)).thenReturn(List.of(privadaPub));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(particularUser(OTHER_USER_ID)));

        publicationService.findByAuthor(AUTHOR_ID, true, OTHER_USER_ID);

        verify(publicationMapper).toResponse(eq(privadaPub), anyLong(), eq(false), any());
    }

    @Test
    void findAll_variasPublicacionesPrivadas_resuelveAccesoConQueriesBatchNoPorPublicacion() {
        Publication invitada = privatePublication();
        Publication noInvitada = privatePublication();
        noInvitada.setId("pub-2");

        PublicationInvitation invitacion = new PublicationInvitation();
        invitacion.setPublicationId(PUB_ID);
        invitacion.setInvitedUserId(OTHER_USER_ID);
        invitacion.setStatus(PublicationInvitationStatus.ACCEPTED);

        when(publicationRepository.findByActiveTrueAndEndDateBefore(any())).thenReturn(List.of());
        when(publicationRepository.findByActiveTrueOrderByStartDateDesc())
                .thenReturn(List.of(invitada, noInvitada));
        when(publicationInvitationRepository.findByPublicationIdInAndInvitedUserIdAndStatusNot(
                eq(List.of(PUB_ID, "pub-2")), eq(OTHER_USER_ID), eq(PublicationInvitationStatus.DECLINED)))
                .thenReturn(List.of(invitacion));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(particularUser(OTHER_USER_ID)));

        publicationService.findAll(true, OTHER_USER_ID);

        verify(publicationMapper).toResponse(eq(invitada), anyLong(), eq(true), any());
        verify(publicationMapper).toResponse(eq(noInvitada), anyLong(), eq(false), any());

        // Una sola query batch por colección, no una por publicación (2 publicaciones privadas).
        verify(publicationEnrollmentRepository, times(1)).findByPublicationIdIn(any());
        verify(publicationEnrollmentRepository, never()).countByPublicationId(any());
        verify(publicationInvitationRepository, times(1))
                .findByPublicationIdInAndInvitedUserIdAndStatusNot(any(), any(), any());
        verify(publicationInvitationRepository, never())
                .existsByPublicationIdAndInvitedUserIdAndStatusNot(any(), any(), any());
        verify(publicationAccessRequestRepository, times(1))
                .findByPublicationIdInAndRequestedByUserIdAndStatus(any(), any(), any());
        verify(publicationAccessRequestRepository, never())
                .existsByPublicationIdAndRequestedByUserIdAndStatus(any(), any(), any());
        // isAdmin() se resuelve una vez para toda la lista, no una vez por publicación.
        verify(userRepository, times(1)).findById(OTHER_USER_ID);
    }
}
