package com.cloudforge.provisioning;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.cloudforge.provisioning.provider.CloudProvisioningProvider;
import com.cloudforge.provisioning.provider.ProviderMode;
import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

/** Test double: answers each call with the next scripted outcome and records every call. */
class ScriptedProvider implements CloudProvisioningProvider {

    record Call(String kind, ProvisioningCommand command, String operationId) {
    }

    private final Deque<Function<ProvisioningCommand, ProvisioningOutcome>> script = new ArrayDeque<>();
    final List<Call> calls = new CopyOnWriteArrayList<>();
    volatile Duration latency = Duration.ZERO;

    synchronized ScriptedProvider then(ProvisioningOutcome outcome) {
        script.add(command -> outcome);
        return this;
    }

    synchronized ScriptedProvider thenThrow(RuntimeException exception) {
        script.add(command -> {
            throw exception;
        });
        return this;
    }

    synchronized void reset() {
        script.clear();
        calls.clear();
        latency = Duration.ZERO;
    }

    @Override
    public ResourceTypeCode type() {
        return ResourceTypeCode.AWS_ACCOUNT;
    }

    @Override
    public ProviderMode mode() {
        return ProviderMode.MOCK;
    }

    @Override
    public ProvisioningOutcome start(ProvisioningCommand command) {
        return answer("START", command, null);
    }

    @Override
    public ProvisioningOutcome poll(ProvisioningCommand command, String operationId) {
        return answer("POLL", command, operationId);
    }

    private ProvisioningOutcome answer(String kind, ProvisioningCommand command, String operationId) {
        calls.add(new Call(kind, command, operationId));
        if (!latency.isZero()) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Function<ProvisioningCommand, ProvisioningOutcome> next;
        synchronized (this) {
            next = script.poll();
        }
        if (next == null) {
            throw new IllegalStateException("No scripted outcome left for " + kind);
        }
        return next.apply(command);
    }
}
