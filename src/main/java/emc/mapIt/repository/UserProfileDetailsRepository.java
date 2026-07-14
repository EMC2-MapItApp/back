package emc.mapIt.repository;

import emc.mapIt.entity.UserProfileDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Acceso a {@link UserProfileDetails}. El {@code @Id} de este documento es el propio
 * {@code userId} del {@link emc.mapIt.entity.User} dueño del perfil (relación 1:1 por clave
 * compartida), no un id generado — de ahí que solo necesite el CRUD heredado.
 */
@Repository
public interface UserProfileDetailsRepository extends MongoRepository<UserProfileDetails, String> {
}