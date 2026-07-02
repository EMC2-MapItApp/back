package emc.mapIt.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "location_types")
public class LocationType {

    @Id
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    private String subCategoryId;

}
