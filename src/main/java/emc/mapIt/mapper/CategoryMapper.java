package emc.mapIt.mapper;

import emc.mapIt.dto.LocationTypeDto;
import emc.mapIt.dto.MainCategoryDto;
import emc.mapIt.dto.SubCategoryDto;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.MainCategory;
import emc.mapIt.entity.SubCategory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

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
                subCategory.getLocationTypes().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }

    public MainCategoryDto toDto(MainCategory mainCategory) {
        return new MainCategoryDto(
                mainCategory.getId(),
                mainCategory.getName(),
                mainCategory.getIcon(),
                mainCategory.getColor(),
                mainCategory.getSubCategories().stream()
                        .map(this::toDto)
                        .collect(Collectors.toList())
        );
    }
}
