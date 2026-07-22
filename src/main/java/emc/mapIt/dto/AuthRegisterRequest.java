package emc.mapIt.dto;

import emc.mapIt.entity.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload para registrar un usuario nuevo.
 * <p>
 * El maximo de 72 caracteres en {@code password} es el limite efectivo de BCrypt (bytes);
 * subirlo no tendria sentido, BCrypt trunca en silencio a partir de ahi
 * (ver {@link emc.mapIt.service.PasswordPolicyService}, que lo rechaza explicitamente).
 * </p>
 * <p>
 * {@code nick} es opcional: si no se envía (o va en blanco), {@link emc.mapIt.service.UserService}
 * genera uno único a partir del nombre.
 * </p>
 */
public record AuthRegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String nick,
        @NotBlank @Email @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserType userType
) {
}
