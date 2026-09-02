package org.electrifyingaustralia.fieldops.exception;

public class UserIdentityConflictException extends RuntimeException {

    public UserIdentityConflictException(String email) {
        super("The email is already linked to another identity: " + email);
    }

    public UserIdentityConflictException(
            String email,
            Throwable cause
    ) {
        super(
                "The user identity or email is already linked: " + email,
                cause
        );
    }
}
