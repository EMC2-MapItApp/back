package emc.mapIt.service;

import emc.mapIt.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("clave-secreta-para-tests-unitarios-1234567890", 3600L);
    }

    @Test
    void generateToken_devuelveStringConDosPartes() {
        String token = jwtService.generateToken("user-abc");
        assertThat(token.split("\\.")).hasSize(2);
    }

    @Test
    void extractUserId_conTokenValido_devuelveElUserId() {
        String userId = "user-xyz";
        String token = jwtService.generateToken(userId);
        assertThat(jwtService.extractUserId("Bearer " + token)).isEqualTo(userId);
    }

    @Test
    void extractUserId_conTokenExpirado_lanzaApiException() {
        // Expiracion negativa -> el token nace ya caducado
        JwtService servicioExpirado = new JwtService("clave-secreta-para-tests-unitarios-1234567890", -1L);
        String tokenExpirado = servicioExpirado.generateToken("user-abc");
        assertThatThrownBy(() -> jwtService.extractUserId("Bearer " + tokenExpirado))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    void extractUserId_sinHeaderBearer_lanzaApiException() {
        assertThatThrownBy(() -> jwtService.extractUserId("token-sin-prefijo"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void extractUserId_conNull_lanzaApiException() {
        assertThatThrownBy(() -> jwtService.extractUserId(null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void extractUserId_conFirmaManipulada_lanzaApiException() {
        String token = jwtService.generateToken("user-abc");
        String manipulado = token.substring(0, token.lastIndexOf('.')) + ".firma-falsa";
        assertThatThrownBy(() -> jwtService.extractUserId("Bearer " + manipulado))
                .isInstanceOf(ApiException.class);
    }
}
