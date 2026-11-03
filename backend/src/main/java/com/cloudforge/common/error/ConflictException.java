package com.cloudforge.common.error;

/** The operation clashes with the current state, for example a duplicate or a concurrent change. */
public class ConflictException extends CloudForgeException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
