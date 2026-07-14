package emc.mapIt.dto;

/**
 * Payload de {@code PUT /api/v1/categories/main/{id}}. Sin anotaciones de validación a
 * propósito: es una edición parcial, un campo {@code null} deja el valor actual sin tocar.
 */
public record UpdateMainCategoryRequest(
        String name,
        String icon,
        String color
) {
}
