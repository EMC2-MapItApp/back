package emc.mapIt.notifications;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.entity.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationSender notificationSender;
    @Mock private PushSender pushSender;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PushSubscriptionRepository pushSubscriptionRepository;
    @Mock private NotificationPreferenceRepository notificationPreferenceRepository;

    private NotificationService notificationService;

    private static final String INVITED_ID = "invited-1";
    private static final String ORGANIZER_ID = "organizer-1";

    private MapItUser invited;
    private MapItUser organizer;

    @BeforeEach
    void setUp() {
        // pushEnabled=true en la mayoría de tests para seguir verificando el mecanismo de
        // fan-out/expiración/best-effort tal cual; los tests específicos del kill-switch
        // reconstruyen el service con pushEnabled=false.
        notificationService = newService(true);

        invited = new MapItUser(INVITED_ID, "Invitado", "invitado@test.com", "hash", UserType.PARTICULAR);
        organizer = new MapItUser(ORGANIZER_ID, "Organizador", "org@test.com", "hash", UserType.PARTICULAR);
    }

    private NotificationService newService(boolean pushEnabled) {
        return new NotificationService(
                notificationSender, pushSender, notificationRepository, pushSubscriptionRepository,
                notificationPreferenceRepository, pushEnabled);
    }

    // ── notifyGroupInvitation ────────────────────────────────────

    @Test
    void notifyGroupInvitation_enviaEmailYPersisteNotificacionInApp() {
        when(pushSubscriptionRepository.findByUserId(INVITED_ID)).thenReturn(List.of());

        notificationService.notifyGroupInvitation(invited, "Club", organizer, "inv-1");

        verify(notificationSender).sendGroupInvitationEmail(
                "invitado@test.com", "Invitado", "Club", "Organizador", "inv-1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(INVITED_ID);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.GROUP_INVITATION);
        assertThat(captor.getValue().isRead()).isFalse();
    }

    @Test
    void notifyGroupInvitation_conSuscripcionesActivas_envíaPushACadaUna() {
        PushSubscription sub1 = subscription("endpoint-1");
        PushSubscription sub2 = subscription("endpoint-2");
        when(pushSubscriptionRepository.findByUserId(INVITED_ID)).thenReturn(List.of(sub1, sub2));

        notificationService.notifyGroupInvitation(invited, "Club", organizer, "inv-1");

        verify(pushSender).send(eq(sub1), any(PushPayload.class));
        verify(pushSender).send(eq(sub2), any(PushPayload.class));
    }

    @Test
    void notifyGroupInvitation_conSuscripcionCaducada_laBorraYSigueConLasDemas() {
        PushSubscription expired = subscription("endpoint-expirado");
        PushSubscription valid = subscription("endpoint-valido");
        when(pushSubscriptionRepository.findByUserId(INVITED_ID)).thenReturn(List.of(expired, valid));
        doThrow(new PushSubscriptionExpiredException("caducada", null))
                .when(pushSender).send(eq(expired), any());

        notificationService.notifyGroupInvitation(invited, "Club", organizer, "inv-1");

        verify(pushSubscriptionRepository).deleteByEndpoint("endpoint-expirado");
        verify(pushSender).send(eq(valid), any(PushPayload.class));
    }

    @Test
    void notifyGroupInvitation_conFalloDeEntregaPush_noPropagaLaExcepcion() {
        PushSubscription sub = subscription("endpoint-1");
        when(pushSubscriptionRepository.findByUserId(INVITED_ID)).thenReturn(List.of(sub));
        doThrow(new PushDeliveryException("fallo de red", null)).when(pushSender).send(eq(sub), any());

        notificationService.notifyGroupInvitation(invited, "Club", organizer, "inv-1");

        verify(pushSubscriptionRepository, never()).deleteByEndpoint(anyString());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyGroupInvitation_conPushDeshabilitadoGlobalmente_persisteInAppPeroNoIntentaPush() {
        notificationService = newService(false);

        notificationService.notifyGroupInvitation(invited, "Club", organizer, "inv-1");

        verify(notificationRepository).save(any(Notification.class));
        verify(pushSubscriptionRepository, never()).findByUserId(anyString());
        verify(pushSender, never()).send(any(), any());
    }

    @Test
    void notifyGroupInvitation_conTipoSilenciadoPorElUsuario_noEnviaEmailPeroSiguePersistiendoInApp() {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(INVITED_ID);
        preference.setMutedEmailTypes(new java.util.HashSet<>(List.of(NotificationType.GROUP_INVITATION)));
        when(notificationPreferenceRepository.findByUserId(INVITED_ID)).thenReturn(Optional.of(preference));
        when(pushSubscriptionRepository.findByUserId(INVITED_ID)).thenReturn(List.of());

        notificationService.notifyGroupInvitation(invited, "Club", organizer, "inv-1");

        verify(notificationSender, never()).sendGroupInvitationEmail(any(), any(), any(), any(), any());
        verify(notificationRepository).save(any(Notification.class));
    }

    // ── notifyGroupSignupInvitationEmail ─────────────────────────

    @Test
    void notifyGroupSignupInvitationEmail_soloEnviaEmail_sinPersistirNiPush() {
        notificationService.notifyGroupSignupInvitationEmail("nuevo@test.com", "Club", "Organizador");

        verify(notificationSender).sendGroupSignupInvitationEmail("nuevo@test.com", "Club", "Organizador");
        verify(notificationRepository, never()).save(any());
        verify(pushSubscriptionRepository, never()).findByUserId(anyString());
    }

    // ── notifyGroupOrganizerNotice / notifyGroupBroadcast ────────

    @Test
    void notifyGroupOrganizerNotice_enviaEmailYPersisteNotificacion() {
        when(pushSubscriptionRepository.findByUserId(ORGANIZER_ID)).thenReturn(List.of());

        notificationService.notifyGroupOrganizerNotice(organizer, "Club", invited, "Asunto", "Mensaje");

        verify(notificationSender).sendGroupOrganizerNoticeEmail(
                "org@test.com", "Organizador", "Club", "Invitado", "Asunto", "Mensaje");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyGroupBroadcast_enviaEmailYPersisteNotificacion() {
        when(pushSubscriptionRepository.findByUserId(INVITED_ID)).thenReturn(List.of());

        notificationService.notifyGroupBroadcast(invited, "Club", organizer, "Asunto", "Mensaje");

        verify(notificationSender).sendGroupBroadcastEmail(
                "invitado@test.com", "Invitado", "Club", "Organizador", "Asunto", "Mensaje");
        verify(notificationRepository).save(any(Notification.class));
    }

    // ── Centro in-app ─────────────────────────────────────────────

    @Test
    void markRead_conNotificacionPropiaNoLeida_laMarcaLeida() {
        Notification notification = new Notification();
        notification.setId("n-1");
        notification.setUserId(INVITED_ID);
        notification.setRead(false);
        when(notificationRepository.findByIdAndUserId("n-1", INVITED_ID)).thenReturn(Optional.of(notification));

        notificationService.markRead(INVITED_ID, "n-1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().isRead()).isTrue();
        assertThat(captor.getValue().getReadAt()).isNotNull();
    }

    @Test
    void markRead_yaLeida_noVuelveAGuardar() {
        Notification notification = new Notification();
        notification.setId("n-1");
        notification.setUserId(INVITED_ID);
        notification.setRead(true);
        when(notificationRepository.findByIdAndUserId("n-1", INVITED_ID)).thenReturn(Optional.of(notification));

        notificationService.markRead(INVITED_ID, "n-1");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllRead_marcaTodasLasNoLeidas() {
        Notification n1 = new Notification();
        n1.setRead(false);
        Notification n2 = new Notification();
        n2.setRead(false);
        when(notificationRepository.findByUserIdAndReadFalse(INVITED_ID)).thenReturn(List.of(n1, n2));

        notificationService.markAllRead(INVITED_ID);

        assertThat(n1.isRead()).isTrue();
        assertThat(n2.isRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(n1, n2));
    }

    // ── Suscripciones push ───────────────────────────────────────

    @Test
    void registerSubscription_nueva_laCreaConDatosDelDispositivo() {
        PushSubscriptionRequest request = new PushSubscriptionRequest(
                "https://push.example.com/nuevo", new PushSubscriptionRequest.Keys("p256dh-val", "auth-val"));
        when(pushSubscriptionRepository.findByEndpoint(request.endpoint())).thenReturn(Optional.empty());

        notificationService.registerSubscription(INVITED_ID, request);

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(INVITED_ID);
        assertThat(captor.getValue().getEndpoint()).isEqualTo("https://push.example.com/nuevo");
        assertThat(captor.getValue().getP256dhKey()).isEqualTo("p256dh-val");
        assertThat(captor.getValue().getAuthKey()).isEqualTo("auth-val");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void unregisterSubscription_borraPorEndpoint() {
        notificationService.unregisterSubscription("https://push.example.com/x");

        verify(pushSubscriptionRepository).deleteByEndpoint("https://push.example.com/x");
    }

    // ── Preferencias de email por tipo ───────────────────────────

    @Test
    void getPreferences_sinDocumentoPrevio_devuelveTodosLosTiposActivados() {
        when(notificationPreferenceRepository.findByUserId(INVITED_ID)).thenReturn(Optional.empty());

        List<NotificationPreferenceResponse> preferences = notificationService.getPreferences(INVITED_ID);

        assertThat(preferences).hasSize(NotificationType.values().length);
        assertThat(preferences).allMatch(NotificationPreferenceResponse::emailEnabled);
    }

    @Test
    void getPreferences_conTipoSilenciado_loDevuelveDesactivadoYElRestoActivados() {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(INVITED_ID);
        preference.setMutedEmailTypes(new java.util.HashSet<>(List.of(NotificationType.GROUP_BROADCAST)));
        when(notificationPreferenceRepository.findByUserId(INVITED_ID)).thenReturn(Optional.of(preference));

        List<NotificationPreferenceResponse> preferences = notificationService.getPreferences(INVITED_ID);

        assertThat(preferences)
                .filteredOn(p -> p.type() == NotificationType.GROUP_BROADCAST)
                .allMatch(p -> !p.emailEnabled());
        assertThat(preferences)
                .filteredOn(p -> p.type() != NotificationType.GROUP_BROADCAST)
                .allMatch(NotificationPreferenceResponse::emailEnabled);
    }

    @Test
    void updateEmailPreference_desactivarSinDocumentoPrevio_creaUnoNuevoConElTipoSilenciado() {
        when(notificationPreferenceRepository.findByUserId(INVITED_ID)).thenReturn(Optional.empty());

        notificationService.updateEmailPreference(INVITED_ID, NotificationType.GROUP_INVITATION, false);

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(notificationPreferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(INVITED_ID);
        assertThat(captor.getValue().getMutedEmailTypes()).containsExactly(NotificationType.GROUP_INVITATION);
    }

    @Test
    void updateEmailPreference_reactivarUnTipoYaSilenciado_loQuitaDelConjunto() {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(INVITED_ID);
        preference.setMutedEmailTypes(new java.util.HashSet<>(List.of(NotificationType.GROUP_INVITATION)));
        when(notificationPreferenceRepository.findByUserId(INVITED_ID)).thenReturn(Optional.of(preference));

        notificationService.updateEmailPreference(INVITED_ID, NotificationType.GROUP_INVITATION, true);

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(notificationPreferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getMutedEmailTypes()).isEmpty();
    }

    // ── Fixtures ─────────────────────────────────────────────────

    private PushSubscription subscription(String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setUserId(INVITED_ID);
        subscription.setEndpoint(endpoint);
        subscription.setP256dhKey("p256dh");
        subscription.setAuthKey("auth");
        return subscription;
    }
}
