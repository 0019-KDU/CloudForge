package com.cloudforge.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.cloudforge.common.error.NotFoundException;
import com.cloudforge.notification.domain.Notification;
import com.cloudforge.notification.repository.NotificationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A user's own notifications. Every query is scoped to the caller's ID. */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public Page<Notification> list(UUID userId, boolean unreadOnly, Pageable pageable) {
        return unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notifications.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long unreadCount(UUID userId) {
        return notifications.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification markRead(UUID userId, UUID notificationId) {
        Notification notification = notifications.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "Notification was not found."));
        notification.markRead(Instant.now(clock));
        return notification;
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, Instant.now(clock));
    }
}
