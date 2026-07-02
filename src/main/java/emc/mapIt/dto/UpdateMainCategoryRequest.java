package emc.mapIt.dto;

public record UpdateMainCategoryRequest(
        String name,
        String icon,
        String color
) {
}
