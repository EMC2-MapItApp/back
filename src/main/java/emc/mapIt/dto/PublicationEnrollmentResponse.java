package emc.mapIt.dto;

public record PublicationEnrollmentResponse(
        String publicationId,
        String userId,
        Long occupiedSlots,
        Integer maxSlots,
        boolean full) {
}