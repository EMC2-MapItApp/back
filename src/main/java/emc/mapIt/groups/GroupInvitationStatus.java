package emc.mapIt.groups;

/** Estado de una {@link GroupInvitation}. Terminal una vez {@code ACCEPTED} o {@code DECLINED} — no vuelve a {@code PENDING}. */
public enum GroupInvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
