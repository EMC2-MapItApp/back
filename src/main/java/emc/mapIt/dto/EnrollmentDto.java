package emc.mapIt.dto;

import java.time.ZonedDateTime;

/** Un inscrito en una publicación, tal como se expone en {@code GET .../enrollments}. */
public record EnrollmentDto(
        String userId,
        String userName,
        ZonedDateTime enrolledAt) {
}
