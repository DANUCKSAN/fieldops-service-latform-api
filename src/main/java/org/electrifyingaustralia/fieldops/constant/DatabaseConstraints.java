package org.electrifyingaustralia.fieldops.constant;

import org.hibernate.exception.ConstraintViolationException;

public final class DatabaseConstraints {

    private DatabaseConstraints() {
    }

    public static boolean isNamed(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
