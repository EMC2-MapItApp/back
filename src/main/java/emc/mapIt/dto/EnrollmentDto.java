package emc.mapIt.dto;

import java.time.ZonedDateTime;

public record EnrollmentDto(
        String userId,
        String userName,
        ZonedDateTime enrolledAt) {
}
