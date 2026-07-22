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

    /** Información mínima del usuario con invitación pendiente. */
    public record PendingInvitee(String userId, String name, String nick) {}
}
