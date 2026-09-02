package org.electrifyingaustralia.fieldops.exception;

public class InvalidOperationException extends RuntimeException {

    private final String code;

    public InvalidOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
