package emc.mapIt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Proyección de {@link emc.mapIt.entity.LocationType}, hoja del árbol de categorías. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationTypeDto {
    private String id;
    private String name;
    private String description;
}
