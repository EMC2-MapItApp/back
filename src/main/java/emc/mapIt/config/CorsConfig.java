package emc.mapIt.config;

import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
/**
 * Configuracion CORS global para los endpoints de API.
 */
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Override
    /**
     * Habilita CORS sobre la ruta /api/**.
     *
     * @param registry registro de reglas CORS.
     */
    public void addCorsMappings(CorsRegistry registry) {
        log.info("Aplicando configuracion CORS para /api/**");
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
