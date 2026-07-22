package emc.mapIt.groups;

import jakarta.validation.constraints.NotBlank;

/** Payload para invitar a un usuario ya existente (resuelto vía búsqueda por nick/email) a un grupo. */
public record InviteUserRequest(@NotBlank String userId) {
}
