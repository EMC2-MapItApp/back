package emc.mapIt.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Configuración global de Jackson para la serialización/deserialización JSON.
 * <p>
 * Registra soporte para tipos de fecha/hora de Java 8 ({@code Instant},
 * {@code LocalDate}, {@code LocalDateTime}) y deshabilita la serialización
 * de fechas como timestamps numéricos para que se exporten como cadenas ISO-8601.
 * </p>
 *
 * <ul>
 *   <li>{@code Instant} → {@code "2026-06-19T16:08:43.195Z"}</li>
 *   <li>{@code LocalDate} → {@code "1998-04-21"}</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    /**
     * Construye el {@link ObjectMapper} principal reutilizando el builder de Spring Boot,
     * que ya incluye módulos del classpath (Jackson2ObjectMapperBuilder auto-detecta
     * {@code JavaTimeModule} si {@code jackson-datatype-jsr310} está en el classpath).
     * <p>
     * Se deshabilita {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} para que
     * las fechas se serialicen como texto ISO-8601 en lugar de milisegundos/arrays.
     * </p>
     *
     * @param builder builder proporcionado por Spring Boot con auto-configuración
     * @return mapper configurado con soporte de fechas Java 8
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
