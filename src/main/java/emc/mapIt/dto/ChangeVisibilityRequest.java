package emc.mapIt.dto;

import emc.mapIt.entity.PublicationVisibility;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de cambio de visibilidad de una publicación existente.
 */
public record ChangeVisibilityRequest(
        @NotNull PublicationVisibility visibility) {
}
