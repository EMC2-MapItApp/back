package emc.mapIt.dto;

/**
 * Respuesta de registro (Fase 1 auth). No incluye token: el registro ya no autentica,
 * el usuario debe verificar su email antes de poder hacer login.
 */
public record RegisterResponse(
        String email
) {
}
