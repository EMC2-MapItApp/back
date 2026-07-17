package emc.mapIt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para el cambio de contraseña de un usuario autenticado.
 * Requiere la contraseña actual (para verificar identidad) y la nueva.
 */
public record ChangePasswordRequest(

        /** Contraseña actual del usuario, para verificación. */
        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword,

        /** Nueva contraseña deseada. */
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String newPassword
) {}
