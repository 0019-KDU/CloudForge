package com.cloudforge.common.error;

/** The request is syntactically valid JSON but asks for something unsupported, such as an unknown sort field. */
public class InvalidRequestException extends CloudForgeException {

    public InvalidRequestException(String code, String message) {
        super(code, message);
    }
}
