package org.electrifyingaustralia.fieldops.exception;

public class InvalidIdentityClaimsException extends RuntimeException {

    public InvalidIdentityClaimsException(String reason) {
        super("Access token cannot create a FieldOps profile: " + reason);
    }
}
