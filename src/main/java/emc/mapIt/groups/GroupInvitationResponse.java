package emc.mapIt.groups;

import java.time.Instant;

/**
 * Vista serializable de una invitación. Desnormaliza nombre/categoría/nº de miembros del grupo y
 * nombre de quien invita, resueltos en el momento de la respuesta (no persistidos) para que el
 * frontend pueda pintar la tarjeta de invitación sin una carga adicional.
 * {@code invitedUserName} y {@code invitedUserNick} permiten al organizador identificar a quién
 * invitó en la vista de edición del grupo, sin carga adicional.
 */
public record GroupInvitationResponse(
        String id,
        String groupId,
        String groupName,
        String groupDescription,
        String groupCategoryId,
        long groupMemberCount,
        String invitedUserId,
        String invitedUserName,
        String invitedUserNick,
        String invitedByUserId,
        String invitedByName,
        GroupInvitationStatus status,
        Instant createdAt) {
}
