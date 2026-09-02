package org.electrifyingaustralia.fieldops.exception;

public class UserAccessDisabledException extends RuntimeException {

    public UserAccessDisabledException() {
        super("This FieldOps user profile is disabled");
    }
}
