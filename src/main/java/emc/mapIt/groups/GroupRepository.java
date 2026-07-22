package emc.mapIt.groups;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/** Acceso a {@link Group}. Solo necesita el CRUD heredado: la pertenencia se resuelve vía {@link GroupMemberRepository}. */
@Repository
public interface GroupRepository extends MongoRepository<Group, String> {
}
