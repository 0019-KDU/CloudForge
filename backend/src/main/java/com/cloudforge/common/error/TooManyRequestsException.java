package com.cloudforge.common.error;

import java.time.Duration;

/** The caller exceeded a rate limit (429); {@code retryAfter} becomes the Retry-After header. */
public class TooManyRequestsException extends CloudForgeException {

    private final Duration retryAfter;

    public TooManyRequestsException(String code, String message, Duration retryAfter) {
        super(code, message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
