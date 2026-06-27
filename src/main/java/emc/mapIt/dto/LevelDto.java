package emc.mapIt.dto;

/**
 * Definicion serializable de un nivel de gamificacion.
 */
public record LevelDto(int level, String label, int requiredXp) {
}
