package emc.mapIt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
