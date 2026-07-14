package emc.mapIt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload para solicitar el restablecimiento de contraseña ("olvidé mi contraseña").
 */
public record ForgotPasswordRequest(
        @NotBlank @Email String email
) {
}
