package emc.mapIt.groups;

/**
 * Resumen de la relación de un usuario con un grupo, para que otros módulos (p. ej.
 * publicaciones) sepan qué mostrar sin acceder a los repositorios de este módulo. Si el grupo no
 * existe, {@link GroupService#getMembershipSummary} devuelve un resumen "vacío" en vez de lanzar
 * — ver ese método para el porqué.
 */
public record GroupMembershipSummary(
        String groupId,
        String groupName,
        int memberCount,
        boolean isMember,
        boolean isOrganizer,
        boolean hasPendingJoinRequest) {
}
