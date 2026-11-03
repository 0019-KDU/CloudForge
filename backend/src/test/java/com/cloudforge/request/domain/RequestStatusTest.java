package com.cloudforge.request.domain;

import static com.cloudforge.request.domain.RequestStatus.APPROVED;
import static com.cloudforge.request.domain.RequestStatus.CANCELLED;
import static com.cloudforge.request.domain.RequestStatus.DRAFT;
import static com.cloudforge.request.domain.RequestStatus.PENDING_APPROVAL;
import static com.cloudforge.request.domain.RequestStatus.PROVISIONED;
import static com.cloudforge.request.domain.RequestStatus.PROVISIONING;
import static com.cloudforge.request.domain.RequestStatus.PROVISIONING_FAILED;
import static com.cloudforge.request.domain.RequestStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The full transition matrix: every allowed edge, and every other pair rejected. */
class RequestStatusTest {

    private static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(PENDING_APPROVAL),
            PENDING_APPROVAL, EnumSet.of(APPROVED, REJECTED, CANCELLED),
            APPROVED, EnumSet.of(PROVISIONING),
            PROVISIONING, EnumSet.of(PROVISIONED, PROVISIONING_FAILED),
            PROVISIONING_FAILED, EnumSet.of(PROVISIONING),
            REJECTED, EnumSet.noneOf(RequestStatus.class),
            CANCELLED, EnumSet.noneOf(RequestStatus.class),
            PROVISIONED, EnumSet.noneOf(RequestStatus.class));

    @ParameterizedTest
    @EnumSource(RequestStatus.class)
    void allowsExactlyTheDocumentedTransitions(RequestStatus from) {
        for (RequestStatus to : RequestStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(ALLOWED.get(from).contains(to));
        }
    }
}
