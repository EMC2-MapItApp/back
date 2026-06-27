package emc.mapIt.dto;

/**
 * Definicion serializable de una capacidad.
 */
public record CapabilityDto(String id, String label, int price, boolean purchasable) {
}
