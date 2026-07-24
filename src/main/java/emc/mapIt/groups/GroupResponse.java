package emc.mapIt.groups;

import java.time.Instant;
import java.util.List;

/** Vista serializable de un grupo, con la lista de miembros ya resuelta (misma forma en listado y detalle). */
public record GroupResponse(
        String id,
        String name,
        String description,
        String categoryId,
        String organizerId,
        List<GroupMemberResponse> members,
        List<PendingInvitee> pendingInvitees,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Información mínima del destinatario de una invitación pendiente. Si la invitación aún no
     * ha sido reclamada por un usuario registrado (ver {@link GroupInvitation}), {@code userId}/
     * {@code name}/{@code nick} son {@code null} y {@code email} lleva la dirección invitada.
     */
    public record PendingInvitee(String userId, String name, String nick, String email) {}
}
