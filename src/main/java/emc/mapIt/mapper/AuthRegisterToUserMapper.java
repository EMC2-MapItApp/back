package emc.mapIt.mapper;

import emc.mapIt.dto.AuthRegisterRequest;
import emc.mapIt.entity.User;
import emc.mapIt.service.HashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mapper para transformar el payload de registro en la entidad persistible {@link User}.
 */
@Component
public class AuthRegisterToUserMapper {

    private static final Logger log = LoggerFactory.getLogger(AuthRegisterToUserMapper.class);

    private final HashService hashService;

    /**
     * Constructor con dependencias de transformación técnica.
     *
     * @param hashService servicio para hashear el password de registro
     */
    public AuthRegisterToUserMapper(HashService hashService) {
        this.hashService = hashService;
    }

    /**
     * Convierte un {@link AuthRegisterRequest} en {@link User} normalizando campos básicos.
     *
     * @param request payload recibido del endpoint de registro
     * @return entidad {@link User} lista para persistencia
     */
    public User toEntity(AuthRegisterRequest request) {
        log.debug("Mapeando request de registro a entidad User para email={}", request.email());
        User user = new User();
        user.setName(request.name().trim());
        // El nick es case-sensitive; solo se recortan espacios (el email sí se normaliza a minúsculas)
        // nick puede ser null cuando el cliente no lo envio; AuthService lo sobreescribe
        // con el valor resuelto antes de llamar a UserService.create()
        user.setNick(request.nick() != null ? request.nick().trim() : null);
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(hashService.sha256(request.password()));
        user.setUserType(request.userType());
        return user;
    }
}

