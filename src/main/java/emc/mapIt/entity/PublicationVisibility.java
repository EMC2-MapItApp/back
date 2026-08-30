package emc.mapIt.entity;

/**
 * Visibilidad de una {@link Publication}: {@code PUBLIC} admite inscripciones de cualquiera hasta
 * completar aforo; {@code PRIVATE} solo admite inscripciones de usuarios con una
 * {@code PublicationInvitation} para esa publicación en estado distinto de {@code DECLINED} (o el
 * propio autor/un ADMIN) — independiente de cualquier grupo. Invitar a los integrantes de un grupo
 * es solo un atajo para rellenar la lista de invitados individuales de una vez; no ata la
 * publicación al grupo ni convierte la pertenencia (presente o futura) en una vía de acceso.
 * Documentos persistidos antes de introducir este campo lo deserializan a {@code null} — debe
 * tratarse como {@code PUBLIC} en toda lectura (ver {@code PublicationMapper#toResponse}), no
 * asumir que el valor por defecto de la propiedad Java cubre ese caso.
 */
public enum PublicationVisibility {
    PUBLIC,
    PRIVATE
}
