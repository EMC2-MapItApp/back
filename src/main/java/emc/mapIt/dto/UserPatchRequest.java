package emc.mapIt.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload de actualización parcial del perfil de usuario.
 * <p>
 * Todos los campos son opcionales. Solo los campos no nulos serán aplicados
 * en la operación de actualización. Los campos de texto opcionales se
 * normalizan:
 * cadena vacía equivale a {@code null} (se elimina el valor).
 * </p>
 *
 * <ul>
 * <li>{@code name}: actualiza {@code users.name}</li>
 * <li>{@code avatarUrl}, {@code phone}, {@code city}, {@code province},
 * {@code bio}, {@code birthDate}: actualizan {@code user_profile_details}</li>
 * <li>{@code favoriteLocationTypeIds}: solo para usuarios tipo INDIVIDUAL</li>
 * </ul>
 */
public record UserPatchRequest(

                /** Nombre visible del usuario. */
                @Size(max = 100) String name,

                /** URL del avatar del usuario. */
                String avatarUrl,

                /** Teléfono de contacto (opcional). */
                @Size(max = 25) String phone,

                /** Ciudad de residencia (opcional). */
                @Size(max = 100) String city,

                /** Provincia de residencia (opcional). */
                @Size(max = 100) String province,

                /** Descripción biográfica del usuario (opcional). */
                @Size(max = 1000) String bio,

                /**
                 * Fecha de nacimiento (opcional, debe ser pasada).
                 * Se serializa/deserializa como {@code yyyy-MM-dd}.
                 */
                @Past LocalDate birthDate,

                /** Lista de tipos de ubicación favoritos (solo INDIVIDUAL). */
                List<String> favoriteLocationTypeIds) {
}