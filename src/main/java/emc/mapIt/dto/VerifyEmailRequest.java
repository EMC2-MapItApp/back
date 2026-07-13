package emc.mapIt.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload para confirmar el email de un usuario tras el registro.
 */
public record VerifyEmailRequest(
        @NotBlank String token
) {
}
