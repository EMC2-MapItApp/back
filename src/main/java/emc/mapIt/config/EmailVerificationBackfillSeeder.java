package emc.mapIt.config;

import emc.mapIt.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Migracion de un solo uso: al introducir el campo {@code emailVerified} (Fase 1 auth),
 * los usuarios que ya existian se registraron bajo el modelo de confianza anterior, asi
 * que se marcan como verificados para no bloquearles el login.
 * <p>
 * Idempotente via un documento marcador en la coleccion {@code _migrations} — corre una
 * unica vez por entorno. <b>No convertir esto en un job que se ejecute sin ese marcador</b>:
 * sin el, verificaria tambien a los usuarios nuevos que aun no han confirmado su email,
 * anulando la Fase 1 por completo.
 * </p>
 */
@Component
public class EmailVerificationBackfillSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationBackfillSeeder.class);
    private static final String MIGRATIONS_COLLECTION = "_migrations";
    private static final String MIGRATION_ID = "fase1-email-verified-backfill";

    private final MongoTemplate mongoTemplate;

    public EmailVerificationBackfillSeeder(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        Query markerQuery = Query.query(Criteria.where("_id").is(MIGRATION_ID));
        if (mongoTemplate.exists(markerQuery, MIGRATIONS_COLLECTION)) {
            log.debug("Backfill emailVerified ya aplicado, se omite.");
            return;
        }

        Query usuariosSinVerificar = Query.query(Criteria.where("emailVerified").ne(true));
        Update marcarVerificado = Update.update("emailVerified", true);
        long actualizados = mongoTemplate.updateMulti(usuariosSinVerificar, marcarVerificado, User.class).getModifiedCount();

        // Map mutable: mongoTemplate.insert() intenta rellenar el _id si faltase,
        // lo que falla con java.util.UnsupportedOperationException sobre un Map.of() inmutable.
        Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("_id", MIGRATION_ID);
        marker.put("appliedAt", Instant.now());
        mongoTemplate.insert(marker, MIGRATIONS_COLLECTION);
        log.info("Backfill emailVerified aplicado, usuarios actualizados={}", actualizados);
    }
}
