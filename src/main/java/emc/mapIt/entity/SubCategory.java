package emc.mapIt.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Nivel intermedio del árbol de categorías, hijo de un {@link MainCategory} vía {@code mainCategoryId}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "sub_categories")
public class SubCategory {

    @Id
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String icon;

    private String mainCategoryId;

}
