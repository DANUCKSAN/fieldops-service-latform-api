package org.electrifyingaustralia.fieldops.entity;

import jakarta.persistence.*;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.electrifyingaustralia.fieldops.enums.UserRole;
import org.electrifyingaustralia.fieldops.enums.UserStatus;

@Getter
@Entity
@Table(name = "app_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "identity_issuer", length = 512)
    private String identityIssuer;

    @Column(name = "identity_subject", length = 255)
    private String identitySubject;

    private User(
            String firstName,
            String lastName,
            String email,
            UserRole role
    ) {
        this.firstName = requireText(firstName, "firstName");
        this.lastName = requireText(lastName, "lastName");
        this.email = normalizeEmail(email);
        this.role = Objects.requireNonNull(role, "role is required");
        this.status = UserStatus.ACTIVE;
    }

    public static User create(
            String firstName,
            String lastName,
            String email,
            UserRole role
    ) {
        return new User(firstName,lastName, email, role);
    }

    private static String normalizeEmail(String email) {
        return requireText(email, "email")
                .toLowerCase(Locale.ROOT);
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        return value.trim();
    }

    public void linkIdentity(String issuer, String subject) {
        if (identityIssuer != null || identitySubject != null) {
            throw new IllegalStateException("User identity is already linked");
        }

        this.identityIssuer = requireText(issuer, "identityIssuer");
        this.identitySubject = requireText(subject, "identitySubject");
    }

    public void synchronizeIdentityProfile(
            String firstName,
            String lastName,
            String email,
            UserRole role
    ) {
        this.firstName = requireText(firstName, "firstName");
        this.lastName = requireText(lastName, "lastName");
        this.email = normalizeEmail(email);
        this.role = Objects.requireNonNull(role, "role is required");
    }
}
