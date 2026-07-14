package emc.mapIt.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload de {@code POST /api/v1/categories/main} para crear una categoría raíz. */
public record CreateMainCategoryRequest(
        @NotBlank String name,
        @NotBlank String icon,
        @NotBlank String color
) {
}
