package emc.mapIt.dto;

import emc.mapIt.entity.PublicationVisibility;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de cambio de visibilidad de una publicación existente.
 * <p>
 * {@code groupId} es obligatorio solo cuando {@code visibility == PRIVATE_GROUP} — se ignora si
 * {@code visibility == PUBLIC} (pasar de privada a pública siempre limpia el grupo asociado).
 * </p>
 */
public record ChangeVisibilityRequest(
        @NotNull PublicationVisibility visibility,
        String groupId) {
}
