package emc.mapIt.dto;

import emc.mapIt.entity.PublicationVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Payload de alta de una publicación tipo actividad/evento.
 * <p>
 * Este contrato se usa para persistir actividades creadas desde el frontend
 * de usuarios particulares. El backend fija el tipo de publicación a EVENT.
 * </p>
 *
 * <h3>Reglas principales</h3>
 * <ul>
 * <li>{@code placeId} debe ser {@code null} para actividades libres de un
 * particular.</li>
 * <li>{@code locationTypeId} debe existir en la jerarquía de categorías.</li>
 * <li>{@code startDate} se interpreta en horario de Madrid y se almacena como
 * fecha zonificada.</li>
 * </ul>
 */
public record CreatePublicationRequest(

        /** Id del lugar asociado, nulo para actividades libres. */
        String placeId,

        /** Id numérico del tipo de ubicación. */
        @NotNull String locationTypeId,

        /** Título visible de la actividad. */
        @NotBlank String title,

        /** Descripción opcional de la actividad. */
        String description,

        /** Fecha y hora de inicio sin zona horaria explícita. */
        @NotNull LocalDateTime startDate,

        /** Fecha y hora de fin sin zona horaria explícita. */
        LocalDateTime endDate,

        /** Latitud del punto de inicio o encuentro. */
        @NotNull BigDecimal lat,

        /** Longitud del punto de inicio o encuentro. */
        @NotNull BigDecimal lng,

        /** Nivel mínimo requerido para ver la actividad. */
        Integer requiredLevel,

        /** Datos dinámicos específicos de la actividad. */
        Map<String, Object> metadata,

        /** Visibilidad de la publicación. Nulo se interpreta como {@code PUBLIC}. */
        PublicationVisibility visibility,

        /** Id del grupo al que se restringe. Obligatorio solo si visibility == PRIVATE_GROUP. */
        String groupId,

        /**
         * Ids de usuarios invitados individualmente al evento, sea cual sea su visibilidad. En
         * {@code PUBLIC} es solo un aviso (ya pueden apuntarse todos). En {@code PRIVATE_GROUP} le
         * permite apuntarse a alguien aunque no pertenezca al grupo — la invitación y la
         * pertenencia al grupo son dos formas independientes de poder apuntarse; ninguna implica
         * la otra (ver {@code PublicationService#enroll}). Nulo o vacío si no se invita a nadie.
         */
        List<String> inviteUserIds) {
}
