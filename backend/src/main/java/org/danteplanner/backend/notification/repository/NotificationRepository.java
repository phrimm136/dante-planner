package org.danteplanner.backend.notification.repository;

import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Notification entities.
 * Supports notification inbox queries, unread counts, and batch operations.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find notification by public UUID.
     */
    Optional<Notification> findByPublicId(UUID publicId);

    /**
     * Whether the recipient already carries a notification for this content.
     *
     * <p>The three columns are those of {@code uk_notification_dedup} and no others: the key does
     * not include {@code deleted_at}, so a soft-deleted row still occupies it and must still count
     * as carried.</p>
     *
     * @param userId           the recipient
     * @param contentId        the content the notification is about
     * @param notificationType the notification kind
     * @return true when a row already occupies the deduplication key
     */
    boolean existsByUserIdAndContentIdAndNotificationType(
            Long userId, String contentId, NotificationType notificationType);

    /**
     * Find notifications for a user's inbox (non-deleted, ordered by creation time).
     */
    Page<Notification> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Count unread notifications for a user.
     */
    long countByUserIdAndReadFalseAndDeletedAtIsNull(Long userId);

    /**
     * Mark a single notification as read.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.id = :id AND n.read = false")
    int markAsRead(@Param("id") Long id, @Param("readAt") Instant readAt);

    /**
     * Mark all unread notifications as read for a user.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.userId = :userId AND n.read = false AND n.deletedAt IS NULL")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);

    /**
     * Soft-delete old read notifications (older than cutoff date).
     */
    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = :deletedAt WHERE n.read = true AND n.createdAt < :cutoffDate AND n.deletedAt IS NULL")
    int softDeleteOldReadNotifications(@Param("cutoffDate") Instant cutoffDate, @Param("deletedAt") Instant deletedAt);

    /**
     * Hard-delete soft-deleted notifications (older than cutoff date).
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.deletedAt IS NOT NULL AND n.deletedAt < :cutoffDate")
    int hardDeleteOldNotifications(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Soft-delete all notifications for a user.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = :deletedAt WHERE n.userId = :userId AND n.deletedAt IS NULL")
    int softDeleteAllByUserId(@Param("userId") Long userId, @Param("deletedAt") Instant deletedAt);

    /**
     * Fan a PLANNER_PUBLISHED notification out to every subscriber in one statement.
     *
     * <p>Inserts one row per user who has new-publication notifications enabled and is not the
     * author, selecting straight from {@code user_settings}. {@code INSERT IGNORE} lets the
     * {@code uk_notification_dedup} unique constraint absorb a re-published planner without a
     * per-recipient round trip, and the projection generates the required {@code public_id} and
     * encodes the planner UUID to binary so no application-side row construction is needed.</p>
     *
     * @return the number of notification rows inserted
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO notifications
                (user_id, content_id, notification_type, public_id, planner_id, planner_title)
            SELECT s.user_id, :plannerId, 'PLANNER_PUBLISHED', UUID_TO_BIN(UUID()),
                   UUID_TO_BIN(:plannerId), :plannerTitle
            FROM user_settings s
            JOIN users u ON u.id = s.user_id
            WHERE s.notify_new_publications = true
              AND u.deleted_at IS NULL
              AND s.user_id <> :authorId
            """, nativeQuery = true)
    int insertPublishedFanout(
            @Param("authorId") Long authorId,
            @Param("plannerId") String plannerId,
            @Param("plannerTitle") String plannerTitle);
}
