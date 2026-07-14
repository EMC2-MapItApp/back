package emc.mapIt.entity;

/** Distingue si una {@link Publication} es una promoción ligada a un {@link Place} o un evento puntual. */
public enum PublicationType {
    /** Promoción asociada a un {@link Place} existente ({@code placeId} obligatorio). */
    PROMOTION,
    /** Evento puntual con coordenadas propias, con o sin lugar asociado. */
    EVENT
}