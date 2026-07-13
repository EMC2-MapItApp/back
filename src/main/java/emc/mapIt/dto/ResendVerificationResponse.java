package emc.mapIt.dto;

/**
 * Respuesta generica de reenvio: siempre el mismo mensaje, exista o no el email,
 * para no permitir enumeracion de usuarios registrados.
 */
public record ResendVerificationResponse(
        String message
) {
}
