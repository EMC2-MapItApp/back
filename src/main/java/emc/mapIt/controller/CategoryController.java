package emc.mapIt.controller;

import emc.mapIt.dto.MainCategoryDto;
import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.MainCategory;
import emc.mapIt.entity.SubCategory;
import emc.mapIt.exception.ApiException;
import emc.mapIt.service.CategoryCrudService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de la jerarquía de categorías de la plataforma MapIt.
 * <p>
 * Proporciona endpoints para consultar la estructura completa de categorías y para
 * realizar operaciones CRUD (Crear, Leer, Actualizar, Borrar) sobre cada nivel
 * de la jerarquía: {@link MainCategory}, {@link SubCategory} y {@link LocationType}.
 * </p>
 *
 * <h3>Estructura Jerárquica</h3>
 * <ul>
 *   <li><b>MainCategory</b>: Nivel superior (ej: "Deportes", "Cultura").</li>
 *   <li><b>SubCategory</b>: Nivel intermedio, anidado bajo una MainCategory (ej: "Ciclismo", "Museos").</li>
 *   <li><b>LocationType</b>: Nivel más bajo, anidado bajo una SubCategory (ej: "Rutas de montaña", "Exposiciones de arte").</li>
 * </ul>
 *
 * <h3>Autenticación</h3>
 * <p>
 * La consulta del árbol de categorías es pública. Las operaciones de modificación (CRUD)
 * podrían requerir permisos de administrador en un futuro, pero actualmente son abiertas.
 * </p>
 *
 * @author MapIt Development Team
 * @version 1.2.0
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

    // --- MainCategory Endpoints ---

    /**
     * Crea una nueva categoría principal.
     *
     * @param mainCategory El objeto {@link MainCategory} a crear, recibido en el cuerpo de la petición.
     * @return Un {@link ResponseEntity} con la categoría creada y el estado HTTP 201 (Created).
     * @throws ApiException si los datos de la categoría son inválidos o incompletos.
     */
    @PostMapping("/main")
    public ResponseEntity<MainCategory> createMainCategory(@RequestBody MainCategory mainCategory) {
        log.info("Request to create a new main category: {}", mainCategory.getName());
        MainCategory createdCategory = categoryCrudService.createMainCategory(mainCategory);
        return new ResponseEntity<>(createdCategory, HttpStatus.CREATED);
    }

    /**
     * Recupera una categoría principal por su identificador.
     *
     * @param id El ID de la categoría principal a buscar.
     * @return Un {@link ResponseEntity} con la categoría encontrada y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si la categoría no existe.
     */
    @GetMapping("/main/{id}")
    public ResponseEntity<MainCategory> getMainCategoryById(@PathVariable Long id) {
        log.debug("Request to get main category with id={}", id);
        MainCategory mainCategory = categoryCrudService.findMainCategoryById(id);
        return ResponseEntity.ok(mainCategory);
    }

    /**
     * Actualiza una categoría principal existente.
     * <p>
     * Los campos no proporcionados en el cuerpo de la petición no serán modificados.
     * </p>
     *
     * @param id El ID de la categoría principal a actualizar.
     * @param mainCategory Un objeto {@link MainCategory} con los campos a modificar.
     * @return Un {@link ResponseEntity} con la categoría actualizada y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si la categoría no existe.
     * @throws ApiException si los datos proporcionados para la actualización son inválidos.
     */
    @PutMapping("/main/{id}")
    public ResponseEntity<MainCategory> updateMainCategory(@PathVariable Long id, @RequestBody MainCategory mainCategory) {
        log.info("Request to update main category with id={}", id);
        MainCategory updatedCategory = categoryCrudService.updateMainCategory(id, mainCategory);
        return ResponseEntity.ok(updatedCategory);
    }

    /**
     * Elimina una categoría principal, junto con todas sus subcategorías y tipos de lugar asociados.
     *
     * @param id El ID de la categoría principal a eliminar.
     * @return Un {@link ResponseEntity} vacío con el estado HTTP 204 (No Content).
     * @throws ApiException con código NOT_FOUND si la categoría no existe.
     */
    @DeleteMapping("/main/{id}")
    public ResponseEntity<Void> deleteMainCategory(@PathVariable Long id) {
        log.info("Request to delete main category with id={}", id);
        categoryCrudService.deleteMainCategory(id);
        return ResponseEntity.noContent().build();
    }

    // --- SubCategory Endpoints ---

    /**
     * Crea una nueva subcategoría anidada bajo una categoría principal específica.
     *
     * @param mainCategoryId El ID de la categoría principal padre bajo la cual se creará la subcategoría.
     * @param subCategory El objeto {@link SubCategory} a crear, recibido en el cuerpo de la petición.
     * @return Un {@link ResponseEntity} con la subcategoría creada y el estado HTTP 201 (Created).
     * @throws ApiException con código NOT_FOUND si la categoría principal no existe.
     * @throws ApiException si los datos de la subcategoría son inválidos o incompletos.
     */
    @PostMapping("/{mainCategoryId}/subcategories")
    public ResponseEntity<SubCategory> createSubCategory(@PathVariable Long mainCategoryId, @RequestBody SubCategory subCategory) {
        log.info("Request to create a new sub category '{}' under main category id={}", subCategory.getName(), mainCategoryId);
        SubCategory createdSubCategory = categoryCrudService.createSubCategory(mainCategoryId, subCategory);
        return new ResponseEntity<>(createdSubCategory, HttpStatus.CREATED);
    }

    /**
     * Recupera una subcategoría por su identificador único.
     *
     * @param id El ID de la subcategoría a buscar.
     * @return Un {@link ResponseEntity} con la subcategoría encontrada y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si la subcategoría no existe.
     */
    @GetMapping("/subcategories/{id}")
    public ResponseEntity<SubCategory> getSubCategoryById(@PathVariable Long id) {
        log.debug("Request to get sub category with id={}", id);
        SubCategory subCategory = categoryCrudService.findSubCategoryById(id);
        return ResponseEntity.ok(subCategory);
    }

    /**
     * Actualiza una subcategoría existente.
     * <p>
     * Los campos no proporcionados en el cuerpo de la petición no serán modificados.
     * También permite reasignar la subcategoría a una nueva categoría principal.
     * </p>
     *
     * @param id El ID de la subcategoría a actualizar.
     * @param subCategory Un objeto {@link SubCategory} con los campos a modificar.
     * @return Un {@link ResponseEntity} con la subcategoría actualizada y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si la subcategoría o la nueva categoría principal no existen.
     * @throws ApiException si los datos proporcionados para la actualización son inválidos.
     */
    @PutMapping("/subcategories/{id}")
    public ResponseEntity<SubCategory> updateSubCategory(@PathVariable Long id, @RequestBody SubCategory subCategory) {
        log.info("Request to update sub category with id={}", id);
        SubCategory updatedSubCategory = categoryCrudService.updateSubCategory(id, subCategory);
        return ResponseEntity.ok(updatedSubCategory);
    }

    /**
     * Elimina una subcategoría, junto con todos sus tipos de lugar asociados.
     *
     * @param id El ID de la subcategoría a eliminar.
     * @return Un {@link ResponseEntity} vacío con el estado HTTP 204 (No Content).
     * @throws ApiException con código NOT_FOUND si la subcategoría no existe.
     */
    @DeleteMapping("/subcategories/{id}")
    public ResponseEntity<Void> deleteSubCategory(@PathVariable Long id) {
        log.info("Request to delete sub category with id={}", id);
        categoryCrudService.deleteSubCategory(id);
        return ResponseEntity.noContent().build();
    }

    // --- LocationType Endpoints ---

    /**
     * Crea un nuevo tipo de lugar anidado bajo una subcategoría específica.
     *
     * @param subCategoryId El ID de la subcategoría padre bajo la cual se creará el tipo de lugar.
     * @param locationType El objeto {@link LocationType} a crear, recibido en el cuerpo de la petición.
     * @return Un {@link ResponseEntity} con el tipo de lugar creado y el estado HTTP 201 (Created).
     * @throws ApiException con código NOT_FOUND si la subcategoría no existe.
     * @throws ApiException si los datos del tipo de lugar son inválidos o incompletos.
     */
    @PostMapping("/subcategories/{subCategoryId}/locationtypes")
    public ResponseEntity<LocationType> createLocationType(@PathVariable Long subCategoryId, @RequestBody LocationType locationType) {
        log.info("Request to create a new location type '{}' under sub category id={}", locationType.getName(), subCategoryId);
        LocationType createdLocationType = categoryCrudService.createLocationType(subCategoryId, locationType);
        return new ResponseEntity<>(createdLocationType, HttpStatus.CREATED);
    }

    /**
     * Recupera un tipo de lugar por su identificador único.
     *
     * @param id El ID del tipo de lugar a buscar.
     * @return Un {@link ResponseEntity} con el tipo de lugar encontrado y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si el tipo de lugar no existe.
     */
    @GetMapping("/locationtypes/{id}")
    public ResponseEntity<LocationType> getLocationTypeById(@PathVariable Long id) {
        log.debug("Request to get location type with id={}", id);
        LocationType locationType = categoryCrudService.findLocationTypeById(id);
        return ResponseEntity.ok(locationType);
    }

    /**
     * Actualiza un tipo de lugar existente.
     * <p>
     * Los campos no proporcionados en el cuerpo de la petición no serán modificados.
     * También permite reasignar el tipo de lugar a una nueva subcategoría.
     * </p>
     *
     * @param id El ID del tipo de lugar a actualizar.
     * @param locationType Un objeto {@link LocationType} con los campos a modificar.
     * @return Un {@link ResponseEntity} con el tipo de lugar actualizado y el estado HTTP 200 (OK).
     * @throws ApiException con código NOT_FOUND si el tipo de lugar o la nueva subcategoría no existen.
     * @throws ApiException si los datos proporcionados para la actualización son inválidos.
     */
    @PutMapping("/locationtypes/{id}")
    public ResponseEntity<LocationType> updateLocationType(@PathVariable Long id, @RequestBody LocationType locationType) {
        log.info("Request to update location type with id={}", id);
        LocationType updatedLocationType = categoryCrudService.updateLocationType(id, locationType);
        return ResponseEntity.ok(updatedLocationType);
    }

    /**
     * Elimina un tipo de lugar.
     *
     * @param id El ID del tipo de lugar a eliminar.
     * @return Un {@link ResponseEntity} vacío con el estado HTTP 204 (No Content).
     * @throws ApiException con código NOT_FOUND si el tipo de lugar no existe.
     */
    @DeleteMapping("/locationtypes/{id}")
    public ResponseEntity<Void> deleteLocationType(@PathVariable Long id) {
        log.info("Request to delete location type with id={}", id);
        categoryCrudService.deleteLocationType(id);
        return ResponseEntity.noContent().build();
    }
}
