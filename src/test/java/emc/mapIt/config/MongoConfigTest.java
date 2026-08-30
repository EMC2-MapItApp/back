package emc.mapIt.config;

import emc.mapIt.entity.PublicationVisibility;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre {@link MongoConfig.PublicationVisibilityReadConverter}: un documento persistido con el
 * valor legacy {@code "PRIVATE_GROUP"} (eliminado del enum {@link PublicationVisibility}) no debe
 * hacer fallar la deserialización — de lo contrario, un solo documento así tumba cualquier
 * lectura masiva de publicaciones (el mapa completo).
 */
class MongoConfigTest {

    private final MongoConfig.PublicationVisibilityReadConverter converter =
            new MongoConfig.PublicationVisibilityReadConverter();

    @Test
    void convert_valorLegacyPrivateGroup_seTraduceAPrivate() {
        assertThat(converter.convert("PRIVATE_GROUP")).isEqualTo(PublicationVisibility.PRIVATE);
    }

    @Test
    void convert_valoresVigentes_seDeserializanTalCual() {
        assertThat(converter.convert("PUBLIC")).isEqualTo(PublicationVisibility.PUBLIC);
        assertThat(converter.convert("PRIVATE")).isEqualTo(PublicationVisibility.PRIVATE);
    }
}
