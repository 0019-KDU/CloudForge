package com.cloudforge.provisioning.domain;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Exponential backoff with "equal jitter": the n-th retry waits between half and all of
 * {@code min(maxDelay, baseDelay * 2^(n-1))}. The random half spreads retries of many jobs that
 * failed together (for example during a provider outage), so they do not all hit the provider at
 * the same moment. The fixed half guarantees a minimum pause.
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final RandomGenerator random;

    public RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay, RandomGenerator random) {
        if (maxAttempts < 1 || baseDelay.isNegative() || baseDelay.isZero() || maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("Invalid retry policy");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.random = random;
    }

    /** Whether another start attempt is allowed after {@code attemptsSoFar} have been made. */
    public boolean canRetry(int attemptsSoFar) {
        return attemptsSoFar < maxAttempts;
    }

    /** Delay before the next attempt, given the number of attempts made so far (at least 1). */
    public Duration delayAfter(int attemptsSoFar) {
        int exponent = Math.min(Math.max(attemptsSoFar - 1, 0), 30);
        long capMillis = Math.min(maxDelay.toMillis(), baseDelay.toMillis() << exponent);
        long half = capMillis / 2;
        return Duration.ofMillis(half + random.nextLong(capMillis - half + 1));
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
