package emc.mapIt.service;

import emc.mapIt.dto.CapabilityDto;
import emc.mapIt.dto.CategoryDto;
import emc.mapIt.dto.LevelDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * Provee datos maestros estaticos para el primer corte de API.
 */
public class MasterDataService {

    private static final Logger log = LoggerFactory.getLogger(MasterDataService.class);

    /**
     * Retorna jerarquia de categorias.
     */
    public List<CategoryDto> getCategories() {
        log.debug("Construyendo listado estatico de categorias");
        return List.of(
                new CategoryDto(
                        "deportes",
                        "Deportes",
                        List.of(new CategoryDto.SubCategoryDto(
                                "running",
                                "Running",
                                List.of(
                                        new CategoryDto.LocationTypeDto("pista-running", "Pista de running"),
                                        new CategoryDto.LocationTypeDto("grupo-running", "Grupo running")
                                )
                        ))
                ),
                new CategoryDto(
                        "ocio",
                        "Ocio",
                        List.of(new CategoryDto.SubCategoryDto(
                                "cultural",
                                "Cultural",
                                List.of(
                                        new CategoryDto.LocationTypeDto("museo", "Museo"),
                                        new CategoryDto.LocationTypeDto("teatro", "Teatro")
                                )
                        ))
                )
        );
    }

    /**
     * Retorna niveles de progresion iniciales.
     */
    public List<LevelDto> getLevels() {
        log.debug("Construyendo listado estatico de niveles");
        return List.of(
                new LevelDto(0, "Explorador", 0),
                new LevelDto(1, "Aventurero", 100),
                new LevelDto(2, "Experto", 300),
                new LevelDto(3, "Leyenda", 700)
        );
    }

    /**
     * Retorna capacidades disponibles iniciales.
     */
    public List<CapabilityDto> getCapabilities() {
        log.debug("Construyendo listado estatico de capacidades");
        return List.of(
                new CapabilityDto("chat", "Chat de grupo", 0, false),
                new CapabilityDto("crear-evento-privado", "Crear evento privado", 1, true),
                new CapabilityDto("premium-badge", "Insignia premium", 2, true)
        );
    }
}
