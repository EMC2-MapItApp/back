package emc.mapIt.groups;

import jakarta.validation.constraints.Size;

/** Campos editables de un grupo existente. Todos opcionales (PATCH) — solo el organizador puede aplicarlos. */
public record UpdateGroupRequest(

        @Size(max = 100) String name,

        @Size(max = 500) String description,

        String categoryId) {
}
