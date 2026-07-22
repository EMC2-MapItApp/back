package emc.mapIt.groups;

import org.springframework.stereotype.Component;

/** Mapper de invitaciones: combina la entidad con datos ya resueltos del grupo y de quien invita. */
@Component
public class GroupInvitationMapper {

    /**
     * Convierte una invitación persistida a una vista serializable.
     *
     * @param invitation       entidad persistida
     * @param group            grupo asociado, ya resuelto
     * @param invitedUserName  nombre del usuario invitado, ya resuelto
     * @param invitedUserNick  nick del usuario invitado, ya resuelto
     * @param invitedByName    nombre de quien envió la invitación, ya resuelto
     * @param groupMemberCount número actual de miembros del grupo
     * @return DTO de respuesta
     */
    public GroupInvitationResponse toResponse(GroupInvitation invitation, Group group,
            String invitedUserName, String invitedUserNick,
            String invitedByName, long groupMemberCount) {
        return new GroupInvitationResponse(
                invitation.getId(),
                invitation.getGroupId(),
                group.getName(),
                group.getDescription(),
                group.getCategoryId(),
                groupMemberCount,
                invitation.getInvitedUserId(),
                invitedUserName,
                invitedUserNick,
                invitation.getInvitedByUserId(),
                invitedByName,
                invitation.getStatus(),
                invitation.getCreatedAt());
    }
}
