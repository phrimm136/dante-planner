package org.danteplanner.backend.notification.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.notification.dto.NotificationInboxResponse;
import org.danteplanner.backend.notification.dto.NotificationResponse;
import org.danteplanner.backend.notification.dto.UnreadCountResponse;
import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.repository.NotificationRepository;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A recipient's own view of their notifications: reading them, and marking or clearing what they
 * have seen.
 */
@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ScopedRead<NotificationInboxResponse> getInbox(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notificationPage = notificationRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);

        List<NotificationResponse> notifications = notificationPage.getContent().stream()
                .map(NotificationResponse::fromEntity)
                .toList();

        return new ScopedRead<>(new NotificationInboxResponse(
                notifications,
                notificationPage.getNumber(),
                notificationPage.getSize(),
                notificationPage.getTotalElements(),
                notificationPage.getTotalPages()
        ), inboxScopes(userId));
    }

    @Transactional(readOnly = true)
    public ScopedRead<UnreadCountResponse> getUnreadCount(Long userId) {
        long count = notificationRepository.countByUserIdAndReadFalseAndDeletedAtIsNull(userId);
        return new ScopedRead<>(new UnreadCountResponse(count), inboxScopes(userId));
    }

    private static Set<FreshnessScope> inboxScopes(Long userId) {
        return Set.of(new FreshnessScope.User(userId), new FreshnessScope.Global());
    }

    @Transactional
    public NotificationResponse markAsRead(UUID publicId, Long userId) {
        Notification notification = requireOwned(publicId, userId);

        notification.markAsRead(clock.instant());
        FreshnessDeclarations.declare(new FreshnessScope.User(userId));

        return NotificationResponse.fromEntity(notification);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        int marked = notificationRepository.markAllAsRead(userId, clock.instant());
        FreshnessDeclarations.declare(new FreshnessScope.User(userId));
        return marked;
    }

    @Transactional
    public void deleteNotification(UUID publicId, Long userId) {
        Notification notification = requireOwned(publicId, userId);

        notification.softDelete(clock.instant());
        FreshnessDeclarations.declare(new FreshnessScope.User(userId));
    }

    @Transactional
    public int deleteAllNotifications(Long userId) {
        int deleted = notificationRepository.softDeleteAllByUserId(userId, clock.instant());
        FreshnessDeclarations.declare(new FreshnessScope.User(userId));
        return deleted;
    }

    private Notification requireOwned(UUID publicId, Long userId) {
        return notificationRepository.findByPublicId(publicId)
                .filter(notification -> notification.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Notification", publicId));
    }
}
