package emc.mapIt.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload para autenticacion por credenciales. {@code identifier} acepta tanto un email como
 * un nick prefijado con {@code @} (p.ej. {@code @ana}); {@link emc.mapIt.service.AuthService}
 * decide cuál de los dos es.
 */
public record AuthLoginRequest(
        @NotBlank String identifier,
        @NotBlank String password
) {
}
