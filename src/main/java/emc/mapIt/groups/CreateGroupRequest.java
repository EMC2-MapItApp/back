package emc.mapIt.groups;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload de creación de un grupo. El usuario autenticado que llama al endpoint pasa a ser el
 * organizador; {@code inviteUserIds} es opcional (se puede crear un grupo sin invitar a nadie
 * todavía).
 */
public record CreateGroupRequest(

        @NotBlank @Size(max = 100) String name,

        @NotBlank @Size(max = 500) String description,

        /** Id de {@code MainCategory} que clasifica el grupo. */
        @NotBlank String categoryId,

        /** Ids de usuarios a invitar de inmediato. Null/vacío = sin invitaciones iniciales. */
        List<String> inviteUserIds) {
}
