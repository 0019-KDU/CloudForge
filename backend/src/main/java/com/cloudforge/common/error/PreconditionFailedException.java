package com.cloudforge.common.error;

/**
 * The client's {@code If-Match} version does not match the current resource (412): someone else
 * changed it since the client read it. The client must reload and reapply its change.
 */
public class PreconditionFailedException extends CloudForgeException {

    public PreconditionFailedException(String message) {
        super("PRECONDITION_FAILED", message);
    }
}
