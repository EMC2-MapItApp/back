package emc.mapIt.config;

import emc.mapIt.entity.PublicationVisibility;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;

/**
 * Conversores custom para Spring Data MongoDB.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new ZonedDateTimeWriteConverter(),
                new ZonedDateTimeReadConverter(),
                new PublicationVisibilityReadConverter()
        ));
    }

    /** Convierte a {@link Date} (instante absoluto) al escribir en MongoDB. */
    @WritingConverter
    static class ZonedDateTimeWriteConverter implements Converter<ZonedDateTime, Date> {
        @Override
        public Date convert(ZonedDateTime source) {
            return Date.from(source.toInstant());
        }
    }

    /** Reconstruye un {@link ZonedDateTime} en UTC al leer de MongoDB. */
    @ReadingConverter
    static class ZonedDateTimeReadConverter implements Converter<Date, ZonedDateTime> {
        @Override
        public ZonedDateTime convert(Date source) {
            return source.toInstant().atZone(ZoneOffset.UTC);
        }
    }

    /**
     * Compatibilidad con documentos persistidos antes de eliminar {@code PRIVATE_GROUP} del enum
     * {@link PublicationVisibility} (commit que sustituyó la visibilidad por grupo por acceso
     * individual mediante invitaciones). Sin este converter, un solo documento con
     * {@code visibility: "PRIVATE_GROUP"} hace fallar la deserialización de Spring Data y tumba
     * cualquier lectura masiva de publicaciones (el mapa completo). El valor legacy se trata como
     * {@code PRIVATE} — es la lectura más restrictiva de las dos disponibles hoy, y evita
     * exponer contenido que originalmente se creó como privado.
     */
    @ReadingConverter
    static class PublicationVisibilityReadConverter implements Converter<String, PublicationVisibility> {
        @Override
        public PublicationVisibility convert(String source) {
            if ("PRIVATE_GROUP".equals(source)) {
                return PublicationVisibility.PRIVATE;
            }
            return PublicationVisibility.valueOf(source);
        }
    }
}
