package emc.mapIt.groups;

import java.time.Instant;
import java.util.List;

/**
 * Vista serializable de una invitación. Desnormaliza nombre/categoría/miembros del grupo y
 * nombre de quien invita, resueltos en el momento de la respuesta (no persistidos) para que el
 * frontend pueda pintar la tarjeta de invitación sin una carga adicional.
 * {@code invitedUserName} y {@code invitedUserNick} permiten al organizador identificar a quién
 * invitó en la vista de edición del grupo, sin carga adicional. {@code groupMemberCount} es
 * {@code groupMembers.size()}, incluido aparte para no obligar al frontend a recontar la lista.
 * <p>
 * {@code invitedUserId}/{@code invitedUserName}/{@code invitedUserNick} son {@code null} y
 * {@code invitedEmail} lleva la dirección invitada mientras la invitación no ha sido reclamada
 * por un usuario registrado (ver {@link GroupInvitation}).
 * </p>
 */
public record GroupInvitationResponse(
        String id,
        String groupId,
        String groupName,
        String groupDescription,
        String groupCategoryId,
        long groupMemberCount,
        List<GroupMemberResponse> groupMembers,
        String invitedUserId,
        String invitedUserName,
        String invitedUserNick,
        String invitedEmail,
        String invitedByUserId,
        String invitedByName,
        GroupInvitationStatus status,
        Instant createdAt) {
}
