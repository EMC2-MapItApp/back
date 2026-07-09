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

    @Test
    void sha256_valorConocido() {
        // SHA-256 de "abc" es un valor estandar verificable externamente
        assertThat(hashService.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2ec73b00361bbef0469f5f8c8583c10ba4d4");
    }
}
