package emc.mapIt.groups;

/** Estado de una {@link GroupJoinRequest}. Terminal una vez {@code ACCEPTED} o {@code REJECTED} — no vuelve a {@code PENDING}. */
public enum GroupJoinRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
