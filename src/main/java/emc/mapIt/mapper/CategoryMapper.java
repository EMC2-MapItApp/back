package emc.mapIt.mapper;

import emc.mapIt.dto.LocationTypeDto;
import emc.mapIt.dto.MainCategoryDto;
import emc.mapIt.dto.SubCategoryDto;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.MainCategory;
import emc.mapIt.entity.SubCategory;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class CategoryMapper {

    public LocationTypeDto toDto(LocationType locationType) {
        return new LocationTypeDto(
                locationType.getId(),
                locationType.getName(),
                locationType.getDescription()
        );
    }

    public SubCategoryDto toDto(SubCategory subCategory) {
        return new SubCategoryDto(
                subCategory.getId(),
                subCategory.getName(),
                subCategory.getIcon(),
                Collections.emptyList()
        );
    }

    public MainCategoryDto toDto(MainCategory mainCategory) {
        return new MainCategoryDto(
                mainCategory.getId(),
                mainCategory.getName(),
                mainCategory.getIcon(),
                mainCategory.getColor(),
                Collections.emptyList()
        );
    }
}
