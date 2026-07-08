package emc.mapIt.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Imprime un resumen del entorno al arrancar la aplicación.
 * ApplicationRunner.run() se ejecuta una vez que el contexto
 * Spring está completamente cargado y el servidor está listo.
 */
@Slf4j
@Component
public class StartupLogger implements ApplicationRunner {

    private final Environment environment;

    @Value("${server.port}")
    private int port;

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    public StartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String profile = Arrays.stream(environment.getActiveProfiles())
                .findFirst()
                .orElse("default");

        // Atlas usa mongodb+srv://, local usa mongodb://
        String dbType = mongoUri.startsWith("mongodb+srv") ? "Atlas (cloud)" : "Local";
        String safeUri = maskPassword(mongoUri);

        log.info("--------------------------------------------------");
        log.info("  MapIt Backend arrancado");
        log.info("  Entorno  : {}", profile);
        log.info("  Puerto   : {}", port);
        log.info("  MongoDB  : {} -> {}", dbType, safeUri);
        log.info("--------------------------------------------------");
    }

    /**
     * Oculta la contraseña de la URI para no exponerla en logs.
     * mongodb+srv://user:secret@cluster.net/db
     *           -> mongodb+srv://user:****@cluster.net/db
     */
    private String maskPassword(String uri) {
        return uri.replaceAll("(mongodb(?:\\+srv)?://[^:]+:)[^@]+(@)", "$1****$2");
    }
}