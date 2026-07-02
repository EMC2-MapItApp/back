package emc.mapIt.dto;

import emc.mapIt.entity.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload para registrar un usuario nuevo.
 */
public record AuthRegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 120) String password,
        @NotNull UserType userType
) {
}
