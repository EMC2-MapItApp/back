package emc.mapIt.dto;

import java.util.UUID;

public record PublicationEnrollmentResponse(
        Long publicationId,
        UUID userId,
        Long occupiedSlots,
        Integer maxSlots,
        boolean full) {
}