package com.cloudforge.notification.application;

import java.time.Clock;
import java.time.Instant;

import com.cloudforge.common.logging.Redaction;
import com.cloudforge.notification.domain.Notification;
import com.cloudforge.notification.repository.NotificationRepository;
import com.cloudforge.provisioning.domain.ProvisioningEvents.ProvisioningFinished;
import com.cloudforge.request.domain.RequestEvents.RequestDecided;
import com.cloudforge.request.domain.RequestEvents.RequestSubmitted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns domain events into in-app notifications <em>after</em> the business transaction commits, in
 * a new transaction. A notification therefore never exists for a change that rolled back, and a
 * notification failure can never undo an approval.
 *
 * <p>Trade-off: if the process dies between the commit and this listener, the notification is lost.
 * That is acceptable for in-app messages, which the request status and audit trail duplicate. If
 * notifications ever go to external systems (email, chat) with delivery guarantees, switch to a
 * transactional outbox (see docs/architecture/provisioning-design.md#outbox).
 */
@Component
class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository notifications;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    NotificationListener(NotificationRepository notifications, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.notifications = notifications;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RequestSubmitted event) {
        save(new Notification(event.approverId(), Notification.Type.APPROVAL_REQUESTED,
                "Approval needed: " + event.requestNumber(),
                "Request " + event.requestNumber() + " is waiting for your decision.", event.requestId(), now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RequestDecided event) {
        String comment = event.comment() == null ? "" : " Comment: " + event.comment();
        save(event.approved()
                ? new Notification(event.requesterId(), Notification.Type.REQUEST_APPROVED,
                        "Approved: " + event.requestNumber(),
                        "Your request " + event.requestNumber() + " was approved and is queued for provisioning." + comment,
                        event.requestId(), now())
                : new Notification(event.requesterId(), Notification.Type.REQUEST_REJECTED,
                        "Rejected: " + event.requestNumber(),
                        "Your request " + event.requestNumber() + " was rejected." + comment, event.requestId(), now()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(ProvisioningFinished event) {
        save(event.succeeded()
                ? new Notification(event.requesterId(), Notification.Type.PROVISIONING_SUCCEEDED,
                        "Ready: " + event.requestNumber(),
                        "Your resource for " + event.requestNumber() + " is ready (" + event.message() + ").",
                        event.requestId(), now())
                : new Notification(event.requesterId(), Notification.Type.PROVISIONING_FAILED,
                        "Provisioning failed: " + event.requestNumber(),
                        "Provisioning for " + event.requestNumber() + " failed: " + event.message()
                                + " A platform administrator can retry it.", event.requestId(), now()));
    }

    /**
     * Own transaction, and every failure (including at commit) is caught here: an exception escaping
     * an after-commit listener would reach the API caller as an error for a change that succeeded.
     */
    private void save(Notification notification) {
        try {
            newTransaction.executeWithoutResult(status -> notifications.save(notification));
        } catch (RuntimeException e) {
            // The business change is already committed; losing an in-app message must not surface as an error.
            log.error("Could not store {} notification for request {}", notification.getType(),
                    notification.getRequestId(), Redaction.redact(e));
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
