package org.danteplanner.backend.notification.repository;

import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.Assert;

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
     * The row occupying a deduplication key.
     *
     * <p>The three columns are those of {@code uk_notification_dedup} and no others: the key does
     * not include {@code deleted_at}, so a soft-deleted row still occupies it and is still what
     * comes back.</p>
     *
     * @param userId           the recipient
     * @param contentId        the content the notification is about
     * @param notificationType the notification kind
     * @return the row on that key, empty when the key is free
     */
    Optional<Notification> findByUserIdAndContentIdAndNotificationType(
            Long userId, String contentId, NotificationType notificationType);

    /**
     * Write one notification unless its deduplication key is already occupied.
     *
     * <p>The constraint decides, not a preceding existence check. A dispatch replayed after a
     * crash re-runs this statement and writes nothing the second time, which is the property the
     * relay depends on.</p>
     *
     * @param userId          the recipient
     * @param contentId       the content the notification is about
     * @param type            the notification kind, as its stored name
     * @param plannerId       the planner to navigate to
     * @param plannerTitle    the planner title for display
     * @param commentSnippet  a snippet of the comment that occasioned it, null when there is none
     * @param commentPublicId the comment's public UUID, null when there is none
     * @return 1 when the row was written, 0 when the key was already occupied
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO notifications
                (user_id, content_id, notification_type, public_id, planner_id, planner_title,
                 comment_snippet, comment_public_id)
            VALUES (:userId, :contentId, :type, UUID_TO_BIN(UUID()), UUID_TO_BIN(:plannerId),
                    :plannerTitle, :commentSnippet, UUID_TO_BIN(:commentPublicId))
            """, nativeQuery = true)
    int insertIgnore(
            @Param("userId") Long userId,
            @Param("contentId") String contentId,
            @Param("type") String type,
            @Param("plannerId") String plannerId,
            @Param("plannerTitle") String plannerTitle,
            @Param("commentSnippet") String commentSnippet,
            @Param("commentPublicId") String commentPublicId);

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

    /**
     * Persists a notification that does not exist yet.
     *
     * @param notification the notification to insert, carrying no id
     * @return the persisted notification, carrying its generated id
     * @throws IllegalArgumentException if the notification already carries an id
     */
    default Notification insert(Notification notification) {
        Assert.isNull(notification.getId(), "insert() takes new rows only");
        return save(notification);
    }
}
