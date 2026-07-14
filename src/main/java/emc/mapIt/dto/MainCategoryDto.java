package emc.mapIt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Nodo raíz del árbol de categorías tal como lo consume el frontend en
 * {@code GET /api/v1/categories/tree}, con sus {@link SubCategoryDto} anidados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MainCategoryDto {
    private String id;
    private String name;
    private String icon;
    private String color;
    private List<SubCategoryDto> subCategories;
}
