package emc.mapIt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload para autenticacion por credenciales.
 */
public record AuthLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
