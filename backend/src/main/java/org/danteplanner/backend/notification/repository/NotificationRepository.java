package org.danteplanner.backend.notification.repository;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.notification.entity.Notification;
import org.danteplanner.backend.notification.api.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByPublicIdAndUserId(UUID publicId, Long userId);

    Optional<Notification> findByUserIdAndContentIdAndNotificationType(
            Long userId, String contentId, NotificationType notificationType);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO notifications
                (user_id, content_id, notification_type, public_id, planner_id, planner_title,
                 comment_snippet, comment_public_id)
            SELECT u.id, :contentId, :type, UUID_TO_BIN(UUID()), UUID_TO_BIN(:plannerId),
                   :plannerTitle, :commentSnippet, UUID_TO_BIN(:commentPublicId)
            FROM users u
            WHERE u.id = :userId
              AND u.deleted_at IS NULL
            """, nativeQuery = true)
    int insertIgnore(
            @Param("userId") Long userId,
            @Param("contentId") String contentId,
            @Param("type") String type,
            @Param("plannerId") @Nullable String plannerId,
            @Param("plannerTitle") @Nullable String plannerTitle,
            @Param("commentSnippet") @Nullable String commentSnippet,
            @Param("commentPublicId") @Nullable String commentPublicId);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :at "
            + "WHERE n.publicId = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markAsRead(@Param("id") UUID publicId, @Param("userId") Long userId, @Param("at") Instant at);

    @Query("SELECT n.id FROM Notification n WHERE n.userId = :userId AND n.readAt IS NULL ORDER BY n.id")
    List<Long> unreadIdsOf(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :at WHERE n.id IN :ids AND n.readAt IS NULL")
    int markAllAsRead(@Param("ids") List<Long> ids, @Param("at") Instant at);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt < :cutoff")
    int deleteReadBefore(@Param("cutoff") Instant cutoff);

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
