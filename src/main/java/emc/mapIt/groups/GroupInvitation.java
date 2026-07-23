package emc.mapIt.groups;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Invitación a un {@link Group}, de un usuario existente o de una dirección de email sin cuenta
 * todavía. Sin uniqueness a nivel de BD sobre {@code groupId}+{@code invitedUserId}/
 * {@code invitedEmail}: tras un {@code DECLINED} debe poder emitirse una nueva invitación, así
 * que la comprobación de "ya existe una invitación pendiente" vive en {@link GroupService} (solo
 * bloquea duplicados en estado {@code PENDING}).
 * <p>
 * Exactamente uno de {@code invitedUserId}/{@code invitedEmail} está poblado en cada momento:
 * una invitación por email nace con {@code invitedUserId == null} y {@code invitedEmail} fijado;
 * en cuanto esa persona se registra y verifica su correo, {@link GroupService} la "reclama" de
 * forma perezosa (al leer las invitaciones pendientes del nuevo usuario) rellenando
 * {@code invitedUserId} y limpiando {@code invitedEmail} — a partir de ahí se comporta como
 * cualquier invitación normal.
 * </p>
 * <p>
 * El propio {@code id} del documento se usa como identificador en el enlace del correo de
 * invitación (query param {@code token} del frontend) — no es un secreto tipo
 * {@code EmailVerificationToken}: aceptar/rechazar siempre valida que el llamador autenticado
 * coincida con {@code invitedUserId}, igual que cualquier otro recurso con id de Mongo en esta
 * app (no hay bypass de autenticación en juego, a diferencia de la verificación de email). Una
 * invitación por email no reclamada no envía ese enlace (no hay cuenta con la que autenticarse
 * todavía) — ver {@code EmailNotificationSender#sendGroupSignupInvitationEmail}.
 * </p>
 */
@Document(collection = "group_invitations")
public class GroupInvitation {

    @Id
    private String id;

    private String groupId;

    private String invitedUserId;

    /** Poblado solo mientras la invitación no ha sido reclamada por un usuario registrado. */
    private String invitedEmail;

    private String invitedByUserId;

    private GroupInvitationStatus status;

    private Instant createdAt;

    private Instant respondedAt;

    public GroupInvitation() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getInvitedUserId() {
        return invitedUserId;
    }

    public void setInvitedUserId(String invitedUserId) {
        this.invitedUserId = invitedUserId;
    }

    public String getInvitedEmail() {
        return invitedEmail;
    }

    public void setInvitedEmail(String invitedEmail) {
        this.invitedEmail = invitedEmail;
    }

    public String getInvitedByUserId() {
        return invitedByUserId;
    }

    public void setInvitedByUserId(String invitedByUserId) {
        this.invitedByUserId = invitedByUserId;
    }

    public GroupInvitationStatus getStatus() {
        return status;
    }

    public void setStatus(GroupInvitationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }
}
