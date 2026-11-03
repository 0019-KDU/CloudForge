package com.cloudforge.provisioning.provider;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Result of one provider call, in CloudForge terms. Adapters translate SDK responses and exceptions
 * into these; the worker never sees provider-specific types.
 */
public sealed interface ProvisioningOutcome {

    /** The resource exists and is ready. {@code details} must not contain secrets. */
    record Completed(String externalResourceId, Map<String, String> details) implements ProvisioningOutcome {

        public Completed {
            Objects.requireNonNull(externalResourceId, "externalResourceId");
            details = Map.copyOf(details);
        }
    }

    /**
     * The provider accepted the request but has not finished. An accepted operation is not a created
     * resource: the worker stores {@code operationId} and calls {@code poll} after {@code pollAfter}.
     */
    record InProgress(String operationId, Duration pollAfter) implements ProvisioningOutcome {

        public InProgress {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(pollAfter, "pollAfter");
        }
    }

    /**
     * The call failed. {@code retryable} marks transient conditions (throttling, timeouts, 5xx);
     * everything else (invalid input, permission denied, name taken) is permanent and is not retried.
     *
     * @param code    stable machine-readable reason, for example {@code PROVIDER_UNAVAILABLE}
     * @param message safe for users and logs: no credentials, tokens or raw provider payloads
     */
    record Failed(String code, String message, boolean retryable) implements ProvisioningOutcome {

        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }

        public static Failed retryable(String code, String message) {
            return new Failed(code, message, true);
        }

        public static Failed permanent(String code, String message) {
            return new Failed(code, message, false);
        }
    }
}
