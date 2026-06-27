package emc.mapIt.dto;

import emc.mapIt.entity.UserType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vista serializable del usuario para respuestas API.
 * <p>
 * Aplana los datos de {@code users} y {@code user_profile_details}
 * en una única respuesta para el cliente.
 * </p>
 *
 * <ul>
 *   <li>Campos de identidad ({@code id}, {@code name}, {@code email}, {@code userType}):
 *       proceden de la tabla {@code users}.</li>
 *   <li>Campos de perfil ({@code phone}, {@code city}, {@code province}, {@code bio},
 *       {@code birthDate}, {@code avatarUrl}, {@code level}, {@code xp},
 *       {@code createdAt}, {@code updatedAt}):
 *       proceden de {@code user_profile_details}.</li>
 *   <li>Listas de capacidades y favoritos: proceden de tablas auxiliares de {@code users}.</li>
 * </ul>
 */
public record MapItUserResponse(

        /** Identificador único del usuario. */
        UUID id,

        /** Nombre visible del usuario. */
        String name,

        /** Email de acceso (normalizado lowercase). */
        String email,

        /** Tipo de usuario: INDIVIDUAL, PROFESSIONAL o ENTITY. */
        UserType userType,

        // --- Gamificación (solo INDIVIDUAL, null para el resto) ---

        /** Nivel de gamificación. Nulo para PROFESSIONAL/ENTITY. */
        Integer level,

        /** Puntos de experiencia acumulados. Nulo para PROFESSIONAL/ENTITY. */
        Integer xp,

        // --- Capacidades y favoritos ---

        /** Lista de ids de capacidades desbloqueadas. */
        List<String> unlockedCapabilities,

        /** Lista de ids de tipos de ubicación favoritos (solo INDIVIDUAL). */
        List<String> favoriteLocationTypeIds,

        // --- Perfil personal ---

        /** URL pública del avatar. Puede ser nula. */
        String avatarUrl,

        /** Teléfono de contacto. Puede ser nulo. */
        String phone,

        /** Ciudad de residencia. Puede ser nula. */
        String city,

        /** Provincia de residencia. Puede ser nula. */
        String province,

        /** Descripción biográfica. Puede ser nula. */
        String bio,

        /** Fecha de nacimiento. Puede ser nula. */
        LocalDate birthDate,

        // --- Timestamps del perfil ---

        /** Fecha y hora de creación del perfil (UTC). */
        Instant createdAt,

        /** Fecha y hora de última modificación del perfil (UTC). */
        Instant updatedAt
) {
}