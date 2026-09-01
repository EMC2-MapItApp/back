package emc.mapIt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuracion CORS global para los endpoints de API.
 * <p>
 * Expone un {@link CorsConfigurationSource} bean que Spring Security consume en su filtro CORS
 * (habilitado con {@code .cors(Customizer.withDefaults())} en {@link SecurityConfig}).
 * Al ejecutarse dentro de la cadena de seguridad, las cabeceras CORS se añaden antes de que
 * cualquier filtro posterior (p.ej. {@link JwtAuthFilter}) pueda cortocircuitar la respuesta,
 * garantizando que los errores 401/403 también lleven {@code Access-Control-Allow-Origin}.
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    // Origen del WebView de Capacitor en Android (ver capacitor.config.ts: androidScheme
    // 'http', sin hostname propio -> sirve el bundle desde http://localhost). No cambia
    // entre entornos: la app puede apuntar al backend de prod desde un dispositivo real.
    private static final String CAPACITOR_ANDROID_ORIGIN = "http://localhost";

    private final String frontendBaseUrl;

    /**
     * Reutiliza {@code mapit.mail.frontend-base-url} (mismo dominio que usan los enlaces de
     * email) en vez de declarar una propiedad de origen CORS aparte.
     */
    public CorsConfig(@Value("${mapit.mail.frontend-base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Registra la política CORS para {@code /api/**}. Spring Security la aplica mediante su
     * {@code CorsFilter}, que corre antes que {@link JwtAuthFilter}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = List.of(frontendBaseUrl, CAPACITOR_ANDROID_ORIGIN);
        log.info("Registrando CorsConfigurationSource para /api/**: origenes permitidos={}", allowedOrigins);
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
