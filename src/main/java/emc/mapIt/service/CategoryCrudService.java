package emc.mapIt.service;

import emc.mapIt.dto.LocationTypeDto;
import emc.mapIt.dto.MainCategoryDto;
import emc.mapIt.dto.SubCategoryDto;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.MainCategory;
import emc.mapIt.entity.SubCategory;
import emc.mapIt.exception.ApiException;
import emc.mapIt.repository.LocationTypeRepository;
import emc.mapIt.repository.MainCategoryRepository;
import emc.mapIt.repository.SubCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD sobre los tres niveles del árbol de categorías (main category → subcategory →
 * location type) y construcción del árbol completo para el frontend.
 */
@Service
@Transactional
public class CategoryCrudService {

    private static final Logger log = LoggerFactory.getLogger(CategoryCrudService.class);

    private final MainCategoryRepository mainCategoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final LocationTypeRepository locationTypeRepository;

    public CategoryCrudService(MainCategoryRepository mainCategoryRepository,
                               SubCategoryRepository subCategoryRepository,
                               LocationTypeRepository locationTypeRepository) {
        this.mainCategoryRepository = mainCategoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.locationTypeRepository = locationTypeRepository;
    }

    /**
     * Construye el árbol completo main category → subcategory → location type. Carga las tres
     * colecciones enteras de una vez (en vez de una query por subcategoría) y agrupa en memoria
     * — a la escala actual del árbol (decenas de nodos) es más barato que N+1 queries.
     */
    @Transactional(readOnly = true)
    public List<MainCategoryDto> getCategoryTree() {
        List<SubCategory> allSubCategories = subCategoryRepository.findAll();
        List<LocationType> allLocationTypes = locationTypeRepository.findAll();

        return mainCategoryRepository.findAll().stream()
                .map(main -> {
                    List<SubCategoryDto> subDtos = allSubCategories.stream()
                            .filter(sub -> main.getId().equals(sub.getMainCategoryId()))
                            .map(sub -> {
                                List<LocationTypeDto> ltDtos = allLocationTypes.stream()
                                        .filter(lt -> sub.getId().equals(lt.getSubCategoryId()))
                                        .map(lt -> new LocationTypeDto(lt.getId(), lt.getName(), lt.getDescription()))
                                        .collect(Collectors.toList());
                                return new SubCategoryDto(sub.getId(), sub.getName(), sub.getIcon(), ltDtos);
                            })
                            .collect(Collectors.toList());
                    return new MainCategoryDto(main.getId(), main.getName(), main.getIcon(), main.getColor(), subDtos);
                })
                .collect(Collectors.toList());
    }

    // --- MainCategory ---

    @Transactional(readOnly = true)
    public MainCategory findMainCategoryById(String id) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "Main category ID is required", HttpStatus.BAD_REQUEST);
        }
        return mainCategoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Main category not found with id: " + id, HttpStatus.NOT_FOUND));
    }

    public MainCategory createMainCategory(MainCategory category) {
        if (category == null) {
            throw new ApiException("BAD_REQUEST", "Main category data is required", HttpStatus.BAD_REQUEST);
        }
        category.setId(null);
        MainCategory saved = mainCategoryRepository.save(category);
        log.info("Main category created with id={}", saved.getId());
        return saved;
    }

    public MainCategory updateMainCategory(String id, MainCategory patch) {
        MainCategory existing = findMainCategoryById(id);

        if (patch.getName() != null && !patch.getName().isBlank()) {
            existing.setName(patch.getName());
        }
        if (patch.getIcon() != null && !patch.getIcon().isBlank()) {
            existing.setIcon(patch.getIcon());
        }
        if (patch.getColor() != null && !patch.getColor().isBlank()) {
            existing.setColor(patch.getColor());
        }

        MainCategory saved = mainCategoryRepository.save(existing);
        log.info("Main category updated with id={}", saved.getId());
        return saved;
    }

    public void deleteMainCategory(String id) {
        if (!mainCategoryRepository.existsById(id)) {
            throw new ApiException("NOT_FOUND", "Main category not found with id: " + id, HttpStatus.NOT_FOUND);
        }
        mainCategoryRepository.deleteById(id);
        log.info("Main category deleted with id={}", id);
    }

    // --- SubCategory ---

    @Transactional(readOnly = true)
    public SubCategory findSubCategoryById(String id) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "Sub category ID is required", HttpStatus.BAD_REQUEST);
        }
        return subCategoryRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Sub category not found with id: " + id, HttpStatus.NOT_FOUND));
    }

    public SubCategory createSubCategory(String mainCategoryId, SubCategory subCategory) {
        if (subCategory == null) {
            throw new ApiException("BAD_REQUEST", "Sub category data is required", HttpStatus.BAD_REQUEST);
        }
        MainCategory mainCategory = findMainCategoryById(mainCategoryId);
        subCategory.setMainCategoryId(mainCategory.getId());
        subCategory.setId(null);
        SubCategory saved = subCategoryRepository.save(subCategory);
        log.info("Sub category created with id={} under main category id={}", saved.getId(), mainCategoryId);
        return saved;
    }

    public SubCategory updateSubCategory(String id, SubCategory patch) {
        SubCategory existing = findSubCategoryById(id);

        if (patch.getName() != null && !patch.getName().isBlank()) {
            existing.setName(patch.getName());
        }
        if (patch.getIcon() != null && !patch.getIcon().isBlank()) {
            existing.setIcon(patch.getIcon());
        }
        if (patch.getMainCategoryId() != null) {
            findMainCategoryById(patch.getMainCategoryId()); // validate exists
            existing.setMainCategoryId(patch.getMainCategoryId());
        }

        SubCategory saved = subCategoryRepository.save(existing);
        log.info("Sub category updated with id={}", saved.getId());
        return saved;
    }

    public void deleteSubCategory(String id) {
        if (!subCategoryRepository.existsById(id)) {
            throw new ApiException("NOT_FOUND", "Sub category not found with id: " + id, HttpStatus.NOT_FOUND);
        }
        subCategoryRepository.deleteById(id);
        log.info("Sub category deleted with id={}", id);
    }

    // --- LocationType ---

    @Transactional(readOnly = true)
    public LocationType findLocationTypeById(String id) {
        if (id == null) {
            throw new ApiException("BAD_REQUEST", "Location type ID is required", HttpStatus.BAD_REQUEST);
        }
        return locationTypeRepository.findById(id)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Location type not found with id: " + id, HttpStatus.NOT_FOUND));
    }

    public LocationType createLocationType(String subCategoryId, LocationType locationType) {
        if (locationType == null) {
            throw new ApiException("BAD_REQUEST", "Location type data is required", HttpStatus.BAD_REQUEST);
        }
        SubCategory subCategory = findSubCategoryById(subCategoryId);
        locationType.setSubCategoryId(subCategory.getId());
        locationType.setId(null);
        LocationType saved = locationTypeRepository.save(locationType);
        log.info("Location type created with id={} under sub category id={}", saved.getId(), subCategoryId);
        return saved;
    }

    public LocationType updateLocationType(String id, LocationType patch) {
        LocationType existing = findLocationTypeById(id);

        if (patch.getName() != null && !patch.getName().isBlank()) {
            existing.setName(patch.getName());
        }
        if (patch.getDescription() != null && !patch.getDescription().isBlank()) {
            existing.setDescription(patch.getDescription());
        }
        if (patch.getSubCategoryId() != null) {
            findSubCategoryById(patch.getSubCategoryId()); // validate exists
            existing.setSubCategoryId(patch.getSubCategoryId());
        }

        LocationType saved = locationTypeRepository.save(existing);
        log.info("Location type updated with id={}", saved.getId());
        return saved;
    }

    public void deleteLocationType(String id) {
        if (!locationTypeRepository.existsById(id)) {
            throw new ApiException("NOT_FOUND", "Location type not found with id: " + id, HttpStatus.NOT_FOUND);
        }
        locationTypeRepository.deleteById(id);
        log.info("Location type deleted with id={}", id);
    }
}
