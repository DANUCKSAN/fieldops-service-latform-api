package org.electrifyingaustralia.fieldops.dto.response;

import java.time.Instant;
import java.util.UUID;
import org.electrifyingaustralia.fieldops.entity.User;
import org.electrifyingaustralia.fieldops.enums.UserRole;
import org.electrifyingaustralia.fieldops.enums.UserStatus;

public record UserResponse (
    UUID id,
    String firstName,
    String lastName,
    String email,
    UserRole role,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt

){
    public static UserResponse from(User user){
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()

        );
    }
}
