package emc.mapIt.groups;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload para invitar por email a alguien que puede no estar registrado todavía — resuelto
 * server-side: si el email ya pertenece a un usuario, se trata como una invitación normal.
 */
public record InviteByEmailRequest(@NotBlank @Email String email) {
}
