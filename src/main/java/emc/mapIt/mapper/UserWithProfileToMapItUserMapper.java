package emc.mapIt.mapper;

import emc.mapIt.domain.MapItUser;
import emc.mapIt.entity.User;
import emc.mapIt.entity.UserProfileDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mapper de composición para construir el modelo de dominio a partir de
 * la entidad principal de usuario y su detalle de perfil asociado.
 */
@Component
public class UserWithProfileToMapItUserMapper {

    private static final Logger log = LoggerFactory.getLogger(UserWithProfileToMapItUserMapper.class);

    /**
     * Convierte una entidad persistida de usuario y su perfil en el modelo de dominio.
     *
     * @param user entidad base del usuario
     * @param profileDetails detalle de perfil asociado (puede ser nulo)
     * @return instancia de dominio {@link MapItUser}
     */
    public MapItUser toDomain(User user, UserProfileDetails profileDetails) {
        if (user == null) {
            return null;
        }

        log.debug("Mapeando User id={} a MapItUser (profileDetailsPresent={})",
                user.getId(), profileDetails != null);

        MapItUser mapItUser = new MapItUser(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getUserType()
        );

        mapItUser.setNick(user.getNick());
        mapItUser.setEmailVerified(user.isEmailVerified());

        if (profileDetails != null) {
            mapItUser.setLevel(profileDetails.getLevel());
            mapItUser.setXp(profileDetails.getXp());
            mapItUser.setAvatarUrl(profileDetails.getAvatarUrl());
        }

        return mapItUser;
    }
}