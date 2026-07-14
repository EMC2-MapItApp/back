package emc.mapIt.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload para canjear un token de restablecimiento y fijar una contraseña nueva.
 */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank String newPassword
) {
}
