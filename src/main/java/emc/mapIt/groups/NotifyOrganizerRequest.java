package emc.mapIt.groups;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload del aviso (email) que un miembro envía al organizador de su grupo. */
public record NotifyOrganizerRequest(@NotBlank @Size(max = 1000) String message) {
}
