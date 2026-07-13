package emc.mapIt.service;

import emc.mapIt.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyServiceTest {

    private final PasswordPolicyService passwordPolicyService = new PasswordPolicyService();

    @Test
    void validate_conPasswordFuerte_noLanzaExcepcion() {
        assertThatCode(() -> passwordPolicyService.validate("Tr0ub4dor&3-xyz!", List.of("Ana", "ana@test.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_conPasswordDebil_lanzaApiException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("12345678", List.of("Ana", "ana@test.com")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("débil");
    }

    @Test
    void validate_conPasswordQueContieneNombreDelUsuario_lanzaApiException() {
        assertThatThrownBy(() -> passwordPolicyService.validate("Eusebio1234", List.of("Eusebio", "eusebio@test.com")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void validate_conPasswordSuperando72Bytes_lanzaApiException() {
        String largaMasDe72Bytes = "a".repeat(73);
        assertThatThrownBy(() -> passwordPolicyService.validate(largaMasDe72Bytes, List.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("72 bytes");
    }
}
