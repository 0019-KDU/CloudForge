package com.cloudforge.common.error;

/**
 * Base type for expected business failures. Each failure carries a stable, machine-readable
 * {@code code} (for example {@code REQUEST_NOT_FOUND}) that API clients can branch on. The message
 * must be safe to show to the caller: never put secrets, SQL or provider responses in it.
 *
 * <p>The HTTP status is decided by {@link GlobalExceptionHandler} from the subtype, so domain code
 * does not depend on the web layer.
 */
public abstract class CloudForgeException extends RuntimeException {

    private final String code;

    protected CloudForgeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
