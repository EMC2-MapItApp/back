package emc.mapIt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload para solicitar el reenvio del correo de verificacion.
 */
public record ResendVerificationRequest(
        @NotBlank @Email String email
) {
}
