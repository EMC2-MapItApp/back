package emc.mapIt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Nodo intermedio del árbol de categorías, con sus {@link LocationTypeDto} hijos anidados. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoryDto {
    private String id;
    private String name;
    private String icon;
    private List<LocationTypeDto> locationTypes;
}
