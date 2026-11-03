package com.cloudforge.provisioning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(15),
            RandomGenerator.getDefault());

    @RepeatedTest(50)
    void delayStaysWithinEqualJitterBounds() {
        for (int attempt = 1; attempt <= 10; attempt++) {
            long cap = Math.min(Duration.ofMinutes(15).toMillis(), 30_000L << (attempt - 1));
            assertThat(policy.delayAfter(attempt).toMillis()).isBetween(cap / 2, cap);
        }
    }

    @Test
    void growsExponentiallyUntilTheCap() {
        RetryPolicy lowest = new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofMinutes(15), fixed(0));
        assertThat(lowest.delayAfter(1)).isEqualTo(Duration.ofSeconds(15));
        assertThat(lowest.delayAfter(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(lowest.delayAfter(3)).isEqualTo(Duration.ofSeconds(60));
        assertThat(lowest.delayAfter(20)).isEqualTo(Duration.ofMinutes(15).dividedBy(2));
    }

    @Test
    void limitsTheNumberOfAttempts() {
        assertThat(policy.canRetry(4)).isTrue();
        assertThat(policy.canRetry(5)).isFalse();
    }

    @Test
    void rejectsNonsensicalConfiguration() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(2), fixed(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofSeconds(10), Duration.ofSeconds(5), fixed(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RandomGenerator fixed(long value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public long nextLong(long bound) {
                return value;
            }
        };
    }
}
