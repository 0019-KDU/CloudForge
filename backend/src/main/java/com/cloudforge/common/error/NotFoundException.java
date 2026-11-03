package com.cloudforge.common.error;

/** The target does not exist, or the caller is not allowed to know that it exists. */
public class NotFoundException extends CloudForgeException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
