package emc.mapIt.groups;

import java.time.Instant;

/** Vista serializable de un miembro de grupo, con datos de usuario ya resueltos. */
public record GroupMemberResponse(
        String userId,
        String name,
        String nick,
        String avatarUrl,
        GroupRole role,
        Instant joinedAt) {
}
