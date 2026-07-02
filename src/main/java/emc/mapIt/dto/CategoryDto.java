package emc.mapIt.dto;

import java.util.List;

/**
 * Nodo de categoria principal con subcategorias y location types.
 */
public record CategoryDto(String id, String name, List<SubCategoryDto> subcategories) {
    /**
     * Nodo de subcategoria.
     */
    public record SubCategoryDto(String id, String name, List<LocationTypeDto> locationTypes) {
    }

    /**
     * Nodo hoja de tipo de ubicacion.
     */
    public record LocationTypeDto(String id, String name) {
    }
}
