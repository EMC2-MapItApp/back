package emc.mapIt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    // La app usa JWT propio — no necesita el UserDetailsService
    // auto-configurado de Spring Security (que genera password aleatoria en logs)
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
@EnableScheduling
public class MapItApplication extends SpringBootServletInitializer {

    /**
     * Punto de entrada para arranque standalone:
     *   java -jar app.war
     *   mvn spring-boot:run -P dev
     */
    public static void main(String[] args) {
        SpringApplication.run(MapItApplication.class, args);
    }

    /**
     * Punto de entrada para Tomcat externo (perfil dev).
     * Tomcat invoca este método en lugar de main() al cargar el WAR.
     * Registra las fuentes de configuración de Spring Boot
     * en el contexto del contenedor servlet.
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(MapItApplication.class);
    }
}