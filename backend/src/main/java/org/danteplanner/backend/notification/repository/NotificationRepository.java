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

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByPublicId(UUID publicId);

    /**
     * The three columns are those of {@code uk_notification_dedup} and no others: the key does
     * not include {@code deleted_at}, so a soft-deleted row still occupies it and is still what
     * comes back.
     */
    Optional<Notification> findByUserIdAndContentIdAndNotificationType(
            Long userId, String contentId, NotificationType notificationType);

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

    Page<Notification> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalseAndDeletedAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.id = :id AND n.read = false")
    int markAsRead(@Param("id") Long id, @Param("readAt") Instant readAt);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.userId = :userId AND n.read = false AND n.deletedAt IS NULL")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);

    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = :deletedAt WHERE n.read = true AND n.createdAt < :cutoffDate AND n.deletedAt IS NULL")
    int softDeleteOldReadNotifications(@Param("cutoffDate") Instant cutoffDate, @Param("deletedAt") Instant deletedAt);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.deletedAt IS NOT NULL AND n.deletedAt < :cutoffDate")
    int hardDeleteOldNotifications(@Param("cutoffDate") Instant cutoffDate);

    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = :deletedAt WHERE n.userId = :userId AND n.deletedAt IS NULL")
    int softDeleteAllByUserId(@Param("userId") Long userId, @Param("deletedAt") Instant deletedAt);

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
