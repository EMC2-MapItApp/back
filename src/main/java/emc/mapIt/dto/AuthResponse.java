package emc.mapIt.dto;

/**
 * Respuesta de autenticacion con token y usuario.
 */
public record AuthResponse(
        String token,
        MapItUserResponse user
) {
}
