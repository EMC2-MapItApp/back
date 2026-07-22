package emc.mapIt.groups;

import emc.mapIt.domain.MapItUser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Mapper de ida y vuelta para grupos y sus miembros. */
@Component
public class GroupMapper {

    /**
     * Construye una entidad Group a partir del request de creación.
     *
     * @param request     datos de entrada
     * @param organizerId id del usuario autenticado que crea el grupo
     * @return entidad lista para persistir
     */
    public Group toEntity(CreateGroupRequest request, String organizerId) {
        Group group = new Group();
        group.setName(request.name().trim());
        group.setDescription(request.description().trim());
        group.setCategoryId(request.categoryId());
        group.setOrganizerId(organizerId);
        group.setCreatedAt(Instant.now());
        return group;
    }

    /**
     * Convierte una pertenencia y su usuario ya resuelto a una vista serializable.
     */
    public GroupMemberResponse toMemberResponse(GroupMember member, MapItUser user) {
        return new GroupMemberResponse(
                member.getUserId(),
                user.getName(),
                user.getNick(),
                user.getAvatarUrl(),
                member.getRole(),
                member.getJoinedAt());
    }

    /**
     * Convierte un grupo persistido y sus miembros ya resueltos a una vista serializable.
     */
    public GroupResponse toResponse(Group group, List<GroupMemberResponse> members,
            List<GroupResponse.PendingInvitee> pendingInvitees) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getCategoryId(),
                group.getOrganizerId(),
                members,
                pendingInvitees,
                group.getCreatedAt(),
                group.getUpdatedAt());
    }
}
