package emc.mapIt.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HashServiceTest {

    private final HashService hashService = new HashService();

    @Test
    void sha256_devuelveHashDe64Caracteres() {
        String hash = hashService.sha256("mapit");
        assertThat(hash).hasSize(64);
    }

    @Test
    void sha256_esIdempotente() {
        assertThat(hashService.sha256("mapit")).isEqualTo(hashService.sha256("mapit"));
    }

    @Test
    void sha256_entradasDistintasProducenHashesDistintos() {
        assertThat(hashService.sha256("aaa")).isNotEqualTo(hashService.sha256("bbb"));
    }


}
