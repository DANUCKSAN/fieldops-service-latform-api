package org.electrifyingaustralia.fieldops.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.electrifyingaustralia.fieldops.dto.response.UserResponse;
import org.electrifyingaustralia.fieldops.entity.User;
import org.electrifyingaustralia.fieldops.enums.UserRole;
import org.electrifyingaustralia.fieldops.enums.UserStatus;
import org.electrifyingaustralia.fieldops.exception.InvalidIdentityClaimsException;
import org.electrifyingaustralia.fieldops.exception.UserAccessDisabledException;
import org.electrifyingaustralia.fieldops.exception.UserIdentityConflictException;
import org.electrifyingaustralia.fieldops.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";
    private static final String FIRST_NAME_CLAIM = "given_name";
    private static final String LAST_NAME_CLAIM = "family_name";
    private static final String ROLES_CLAIM = "roles";

    private final UserRepository userRepository;

    @Transactional
    public UserResponse getOrProvisionCurrentUser(Jwt jwt) {
        return UserResponse.from(resolveAuthenticatedUser(jwt));
    }

    @Transactional
    public User resolveAuthenticatedUser(Jwt jwt) {
        IdentityClaims identity = identityClaims(jwt);

        User user = userRepository
                .findByIdentityIssuerAndIdentitySubject(
                        identity.issuer(),
                        identity.subject()
                )
                .orElseGet(() -> linkOrCreateUser(identity));

        ensureUserIsActive(user);

        user.synchronizeIdentityProfile(
                identity.firstName(),
                identity.lastName(),
                identity.email(),
                identity.role()
        );

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new UserIdentityConflictException(identity.email(), exception);
        }
    }

    private User linkOrCreateUser(IdentityClaims identity) {
        return userRepository
                .findByEmailIgnoreCase(identity.email())
                .map(user -> linkExistingUser(user, identity))
                .orElseGet(() -> createLinkedUser(identity));
    }

    private User linkExistingUser(
            User user,
            IdentityClaims identity
    ) {
        ensureUserIsActive(user);

        if (user.getIdentityIssuer() != null
                || user.getIdentitySubject() != null) {
            throw new UserIdentityConflictException(identity.email());
        }

        user.linkIdentity(
                identity.issuer(),
                identity.subject()
        );

        return user;
    }

    private User createLinkedUser(IdentityClaims identity) {
        User user = User.create(
                identity.firstName(),
                identity.lastName(),
                identity.email(),
                identity.role()
        );

        user.linkIdentity(
                identity.issuer(),
                identity.subject()
        );

        return user;
    }

    private void ensureUserIsActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserAccessDisabledException();
        }
    }

    private IdentityClaims identityClaims(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidIdentityClaimsException(
                    "the authenticated principal is missing"
            );
        }

        String issuer = jwt.getIssuer() == null
                ? null
                : jwt.getIssuer().toExternalForm();

        if (!Boolean.TRUE.equals(
                jwt.getClaimAsBoolean(EMAIL_VERIFIED_CLAIM)
        )) {
            throw new InvalidIdentityClaimsException(
                    "email_verified must be true"
            );
        }

        return new IdentityClaims(
                requiredText(issuer, "iss", 512),
                requiredText(jwt.getSubject(), "sub", 255),
                requiredText(
                        jwt.getClaimAsString(FIRST_NAME_CLAIM),
                        FIRST_NAME_CLAIM,
                        120
                ),
                requiredText(
                        jwt.getClaimAsString(LAST_NAME_CLAIM),
                        LAST_NAME_CLAIM,
                        120
                ),
                requiredText(
                        jwt.getClaimAsString(EMAIL_CLAIM),
                        EMAIL_CLAIM,
                        254
                ),
                fieldOpsRole(jwt.getClaim(ROLES_CLAIM))
        );
    }

    private UserRole fieldOpsRole(Object rolesClaim) {
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);

        if (rolesClaim instanceof Collection<?> claimValues) {
            claimValues.forEach(value -> addFieldOpsRole(roles, value));
        } else if (rolesClaim instanceof String claimValue) {
            for (String value : claimValue.split("\\s+")) {
                addFieldOpsRole(roles, value);
            }
        } else {
            throw new InvalidIdentityClaimsException(
                    "roles must be a list or space-delimited string"
            );
        }

        if (roles.size() != 1) {
            throw new InvalidIdentityClaimsException(
                    "exactly one FieldOps role is required"
            );
        }

        return roles.iterator().next();
    }

    private void addFieldOpsRole(
            Set<UserRole> roles,
            Object claimValue
    ) {
        if (!(claimValue instanceof String roleName)) {
            return;
        }

        try {
            roles.add(UserRole.valueOf(roleName));
        } catch (IllegalArgumentException ignored) {
            // Ignore Keycloak's non-FieldOps realm roles.
        }
    }

    private String requiredText(
            String value,
            String claimName,
            int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            throw new InvalidIdentityClaimsException(
                    claimName + " is required"
            );
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > maximumLength) {
            throw new InvalidIdentityClaimsException(
                    claimName + " exceeds the maximum length"
            );
        }

        return normalizedValue;
    }

    private record IdentityClaims(
            String issuer,
            String subject,
            String firstName,
            String lastName,
            String email,
            UserRole role
    ) {
    }
}
