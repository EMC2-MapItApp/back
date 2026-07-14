package emc.mapIt.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Nivel superior del árbol de categorías ({@link MainCategory} → {@link SubCategory} →
 * {@link LocationType}) que clasifica los lugares. {@code icon}/{@code color} son puramente de
 * presentación (frontend).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "main_categories")
public class MainCategory {

    @Id
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String icon;

    @NotBlank
    private String color;

}
