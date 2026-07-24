package emc.mapIt.groups;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload del aviso (email) que un miembro envía al organizador de su grupo.
 *
 * @param subject asunto elegido por el usuario; se muestra en el cuerpo del correo, no
 *                sustituye el asunto real del email (ver {@code EmailNotificationSender})
 * @param message mensaje del aviso
 */
public record NotifyOrganizerRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 1000) String message) {
}
