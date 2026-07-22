package emc.mapIt.groups;

/** Rol de un usuario dentro de un {@link Group}: quien lo crea es {@code ORGANIZER}, el resto entra como {@code MEMBER} tras aceptar una {@link GroupInvitation}. */
public enum GroupRole {
    ORGANIZER,
    MEMBER
}
