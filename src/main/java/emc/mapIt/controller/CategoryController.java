package emc.mapIt.controller;

import emc.mapIt.dto.MainCategoryDto;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.MainCategory;
import emc.mapIt.entity.SubCategory;
import emc.mapIt.exception.ApiException;
import emc.mapIt.service.CategoryCrudService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST de solo lectura para la jerarquía de categorías de la plataforma MapIt.
 * <p>
 * Expone únicamente consultas: {@link MainCategory}, {@link SubCategory} y {@link LocationType}
 * se gestionan hoy vía {@code CategorySeeder}, no hay formulario en el frontend para crearlas ni
 * editarlas todavía. Los endpoints de escritura (CRUD completo, restringido a {@code ADMIN})
 * existieron en su momento sin ningún consumidor real — se retiraron para no mantener una
 * superficie de API sin cliente ni validación (`@Valid`) completa; la lógica de negocio sigue
 * intacta en {@link CategoryCrudService} para cuando se implemente el dashboard de administración
 * que los use.
 * </p>
 *
 * @author MapIt Development Team
 * @version 1.3.0
 * @since 2026-06-25
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryCrudService categoryCrudService;

    /**
     * Constructor para inyección de dependencias del servicio de categorías.
     *
     * @param categoryCrudService El servicio que encapsula la lógica de negocio para las categorías.
     */
    public CategoryController(CategoryCrudService categoryCrudService) {
        this.categoryCrudService = categoryCrudService;
    }

    /**
     * Recupera el árbol completo y anidado de todas las categorías.
     * <p>
     * Este endpoint es ideal para que el frontend construya menús de navegación,
     * sistemas de filtrado o selectores jerárquicos. La respuesta es una lista
     * de {@link MainCategoryDto}, que contienen sus respectivas subcategorías y tipos de lugar.
     * </p>
     *
     * @return Un {@link ResponseEntity} con una lista de {@link MainCategoryDto} que representa la jerarquía completa.
     * @see CategoryCrudService#getCategoryTree()
     */
    @GetMapping("/tree")
    public ResponseEntity<List<MainCategoryDto>> getCategoryTree() {
        log.info("Request received for the full category tree.");
        List<MainCategoryDto> categoryTree = categoryCrudService.getCategoryTree();
        return ResponseEntity.ok(categoryTree);
    }

    /**
     * Recupera una categoría principal por su identificador.
     *
     * @param id El ID de la categoría principal a buscar.
     * @return Un {@link ResponseEntity} con la categoría encontrada y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si la categoría no existe.
     */
    @GetMapping("/main/{id}")
    public ResponseEntity<MainCategory> getMainCategoryById(@PathVariable String id) {
        log.debug("Request to get main category with id={}", id);
        MainCategory mainCategory = categoryCrudService.findMainCategoryById(id);
        return ResponseEntity.ok(mainCategory);
    }

    /**
     * Recupera una subcategoría por su identificador único.
     *
     * @param id El ID de la subcategoría a buscar.
     * @return Un {@link ResponseEntity} con la subcategoría encontrada y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si la subcategoría no existe.
     */
    @GetMapping("/subcategories/{id}")
    public ResponseEntity<SubCategory> getSubCategoryById(@PathVariable String id) {
        log.debug("Request to get sub category with id={}", id);
        SubCategory subCategory = categoryCrudService.findSubCategoryById(id);
        return ResponseEntity.ok(subCategory);
    }

    /**
     * Recupera un tipo de lugar por su identificador único.
     *
     * @param id El ID del tipo de lugar a buscar.
     * @return Un {@link ResponseEntity} con el tipo de lugar encontrado y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si el tipo de lugar no existe.
     */
    @GetMapping("/locationtypes/{id}")
    public ResponseEntity<LocationType> getLocationTypeById(@PathVariable String id) {
        log.debug("Request to get location type with id={}", id);
        LocationType locationType = categoryCrudService.findLocationTypeById(id);
        return ResponseEntity.ok(locationType);
    }
}
