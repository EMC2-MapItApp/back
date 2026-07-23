package emc.mapIt.groups;

import java.util.List;

import org.springframework.stereotype.Component;

/** Mapper de invitaciones: combina la entidad con datos ya resueltos del grupo y de quien invita. */
@Component
public class GroupInvitationMapper {

    /**
     * Convierte una invitación persistida a una vista serializable.
     *
     * @param invitation      entidad persistida
     * @param group           grupo asociado, ya resuelto
     * @param invitedUserName nombre del usuario invitado, ya resuelto; {@code null} si la
     *                        invitación es por email y aún no se ha reclamado
     * @param invitedUserNick nick del usuario invitado, ya resuelto; {@code null} en el mismo caso
     * @param invitedByName   nombre de quien envió la invitación, ya resuelto
     * @param groupMembers    miembros actuales del grupo, ya resueltos
     * @return DTO de respuesta
     */
    public GroupInvitationResponse toResponse(GroupInvitation invitation, Group group,
            String invitedUserName, String invitedUserNick,
            String invitedByName, List<GroupMemberResponse> groupMembers) {
        return new GroupInvitationResponse(
                invitation.getId(),
                invitation.getGroupId(),
                group.getName(),
                group.getDescription(),
                group.getCategoryId(),
                groupMembers.size(),
                groupMembers,
                invitation.getInvitedUserId(),
                invitedUserName,
                invitedUserNick,
                invitation.getInvitedEmail(),
                invitation.getInvitedByUserId(),
                invitedByName,
                invitation.getStatus(),
                invitation.getCreatedAt());
    }
}
