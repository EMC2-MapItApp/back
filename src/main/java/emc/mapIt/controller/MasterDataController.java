package emc.mapIt.controller;

import emc.mapIt.dto.CapabilityDto;
import emc.mapIt.dto.CategoryDto;
import emc.mapIt.dto.LevelDto;
import emc.mapIt.service.MasterDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
/**
 * Endpoints de lectura de datos maestros.
 */
public class MasterDataController {

    private static final Logger log = LoggerFactory.getLogger(MasterDataController.class);

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

//    @GetMapping("/categories")
//    /**
//     * Obtiene jerarquia de categorias y tipos de ubicacion.
//     */
//    public List<CategoryDto> categories() {
//        log.debug("Consulta de categorias");
//        return masterDataService.getCategories();
//    }

//    @GetMapping("/levels")
//    /**
//     * Obtiene definiciones de niveles de gamificacion.
//     */
//    public List<LevelDto> levels() {
//        log.debug("Consulta de niveles");
//        return masterDataService.getLevels();
//    }

//

}
