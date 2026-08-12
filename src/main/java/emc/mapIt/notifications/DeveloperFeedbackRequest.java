package emc.mapIt.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload del feedback que un usuario autenticado envía al equipo de desarrollo.
 *
 * @param category categoría del feedback ({@code BUG}, {@code SUGGESTION} u {@code OTHER})
 * @param subject  asunto elegido por el usuario; se muestra en el cuerpo del correo, no
 *                 sustituye el asunto real del email (fijo, ver {@code EmailNotificationSender})
 * @param message  mensaje del usuario
 */
public record DeveloperFeedbackRequest(
        @NotNull FeedbackCategory category,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 2000) String message) {
}
