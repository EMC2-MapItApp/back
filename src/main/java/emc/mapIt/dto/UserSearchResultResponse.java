package emc.mapIt.dto;

/**
 * Resultado de búsqueda de usuarios por nick/email (usado por el buscador de invitación a
 * grupos). Vista deliberadamente reducida frente a {@link MapItUserResponse}: no expone
 * capacidades, nivel/XP ni favoritos de un usuario distinto al autenticado.
 */
public record UserSearchResultResponse(
        String id,
        String name,
        String nick,
        String email,
        String avatarUrl) {
}
