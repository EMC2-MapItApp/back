package emc.mapIt.entity;

/**
 * Visibilidad de una {@link Publication}: {@code PUBLIC} admite inscripciones de cualquiera hasta
 * completar aforo; {@code PRIVATE_GROUP} solo admite inscripciones de miembros del grupo
 * referenciado en {@link Publication#getGroupId()}. Documentos persistidos antes de introducir
 * este campo lo deserializan a {@code null} — debe tratarse como {@code PUBLIC} en toda lectura
 * (ver {@code PublicationMapper#toResponse}), no asumir que el valor por defecto de la propiedad
 * Java cubre ese caso.
 */
public enum PublicationVisibility {
    PUBLIC,
    PRIVATE_GROUP
}
