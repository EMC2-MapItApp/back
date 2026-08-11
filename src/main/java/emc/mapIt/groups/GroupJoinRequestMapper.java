package emc.mapIt.groups;

import org.springframework.stereotype.Component;

/** Mapper de solicitudes de acceso: combina la entidad con datos ya resueltos del grupo y del solicitante. */
@Component
public class GroupJoinRequestMapper {

    /**
     * Convierte una solicitud de acceso persistida a una vista serializable.
     *
     * @param request           entidad persistida
     * @param group             grupo asociado, ya resuelto
     * @param requestedByName   nombre de quien solicita, ya resuelto
     * @param requestedByNick   nick de quien solicita, ya resuelto
     * @return DTO de respuesta
     */
    public GroupJoinRequestResponse toResponse(GroupJoinRequest request, Group group,
            String requestedByName, String requestedByNick) {
        return new GroupJoinRequestResponse(
                request.getId(),
                request.getGroupId(),
                group.getName(),
                request.getRequestedByUserId(),
                requestedByName,
                requestedByNick,
                request.getPublicationId(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getRespondedAt());
    }
}
