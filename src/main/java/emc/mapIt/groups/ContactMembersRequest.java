package emc.mapIt.groups;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload del mensaje que el organizador envía a los miembros de su grupo.
 *
 * @param subject           asunto elegido por el usuario; se muestra en el cuerpo del correo,
 *                          no sustituye el asunto real del email (ver
 *                          {@code EmailNotificationSender})
 * @param message           texto del mensaje
 * @param recipientUserIds  ids de miembros destinatarios; {@code null} o vacío significa
 *                          "todos los miembros" (el organizador se excluye siempre)
 */
public record ContactMembersRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 2000) String message,
        List<String> recipientUserIds) {
}
