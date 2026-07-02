package emc.mapIt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubCategoryDto {
    private String id;
    private String name;
    private String icon;
    private List<LocationTypeDto> locationTypes;
}
