
package emc.mapIt.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record EnrollmentDto(
        UUID userId,
        String userName,
        ZonedDateTime enrolledAt) {
} 
    

