package emc.mapIt.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMainCategoryRequest(
        @NotBlank String name,
        @NotBlank String icon,
        @NotBlank String color
) {
}
