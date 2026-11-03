package com.cloudforge.notification.api;

import java.time.Instant;
import java.util.UUID;

import com.cloudforge.common.api.PageResponse;
import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.notification.application.NotificationService;
import com.cloudforge.notification.domain.Notification;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    record NotificationResponse(UUID id, Notification.Type type, String title, String body, UUID requestId,
            Instant readAt, Instant createdAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getRequestId(),
                    n.getReadAt(), n.getCreatedAt());
        }
    }

    record UnreadCountResponse(long unread) {
    }

    record MarkAllReadResponse(int marked) {
    }

    @GetMapping
    PageResponse<NotificationResponse> list(CurrentUser user, @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()); // fixed order: newest first
        return PageResponse.of(notifications.list(user.id(), unreadOnly, page), NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount(CurrentUser user) {
        return new UnreadCountResponse(notifications.unreadCount(user.id()));
    }

    @PostMapping("/{id}/read")
    NotificationResponse markRead(CurrentUser user, @PathVariable UUID id) {
        return NotificationResponse.from(notifications.markRead(user.id(), id));
    }

    @PostMapping("/read-all")
    MarkAllReadResponse markAllRead(CurrentUser user) {
        return new MarkAllReadResponse(notifications.markAllRead(user.id()));
    }
}
