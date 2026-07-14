package emc.mapIt.dto;

/**
 * Respuesta de confirmación de {@code POST /api/v1/auth/forgot-password}.
 */
public record ForgotPasswordResponse(String message) {
}
