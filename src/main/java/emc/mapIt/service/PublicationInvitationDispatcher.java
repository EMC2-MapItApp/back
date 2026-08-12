package emc.mapIt.service;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.entity.Publication;
import emc.mapIt.entity.PublicationInvitation;
import emc.mapIt.entity.PublicationInvitationStatus;
import emc.mapIt.notifications.NotificationService;
import emc.mapIt.repository.PublicationInvitationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Persiste y notifica las invitaciones individuales a una publicación en segundo plano, fuera
 * del hilo de la petición HTTP que crea la publicación. Vive en un bean aparte de
 * {@link PublicationService} porque {@code @Async} de Spring solo intercepta llamadas que pasan
 * por el proxy del bean — una autoinvocación dentro de la misma clase lo ignoraría en silencio.
 * <p>
 * Enviar cada invitación implica un correo SMTP síncrono ({@code JavaMailSender}); con varios
 * invitados esto podía tardar varios segundos y mantenía pendiente la respuesta de creación de
 * la publicación el tiempo suficiente para que un doble click del botón "Crear publicación"
 * disparase dos publicaciones. Al no bloquear la respuesta, la publicación ya está creada
 * (y su botón puede rehabilitarse) mientras las invitaciones se siguen enviando.
 * </p>
 */
@Service
public class PublicationInvitationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PublicationInvitationDispatcher.class);

    private final PublicationInvitationRepository publicationInvitationRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public PublicationInvitationDispatcher(PublicationInvitationRepository publicationInvitationRepository,
            UserService userService,
            NotificationService notificationService) {
        this.publicationInvitationRepository = publicationInvitationRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /**
     * Envía, en un hilo aparte, una invitación por cada id de {@code invitedUserIds}. Cada una es
     * independiente de las demás: si una falla (usuario inexistente, envío de email caído...) se
     * loguea y se continúa con el resto, ya que en este punto la publicación ya se ha devuelto
     * como creada al frontend.
     */
    @Async
    public void dispatchAsync(Publication publication, String authorId, Set<String> invitedUserIds) {
        MapItUser author;
        try {
            author = userService.getByIdOrThrow(authorId);
        } catch (RuntimeException ex) {
            log.warn("No se pudieron enviar las invitaciones de publicationId={}: autor authorId={} no encontrado",
                    publication.getId(), authorId, ex);
            return;
        }

        for (String invitedUserId : invitedUserIds) {
            try {
                sendOne(publication, authorId, invitedUserId, author);
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar la invitación de publicationId={} a invitedUserId={}: {}",
                        publication.getId(), invitedUserId, ex.getMessage(), ex);
            }
        }
    }

    private void sendOne(Publication publication, String authorId, String invitedUserId, MapItUser author) {
        MapItUser invitedUser = userService.getByIdOrThrow(invitedUserId);

        PublicationInvitation invitation = new PublicationInvitation();
        invitation.setPublicationId(publication.getId());
        invitation.setInvitedUserId(invitedUserId);
        invitation.setInvitedByUserId(authorId);
        invitation.setStatus(PublicationInvitationStatus.PENDING);
        invitation.setCreatedAt(Instant.now());
        publicationInvitationRepository.save(invitation);

        notificationService.notifyPublicationInvitation(invitedUser, publication.getTitle(), author);
    }
}
