package emc.mapIt.dto;

/**
 * Respuesta de {@code POST .../enroll}: estado de ocupación de la publicación tras la
 * inscripción, incluyendo si ha llegado a {@code maxSlots} ({@code full}).
 */
public record PublicationEnrollmentResponse(
        String publicationId,
        String userId,
        Long occupiedSlots,
        Integer maxSlots,
        boolean full) {
}