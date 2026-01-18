package org.danteplanner.backend.repository;

import org.danteplanner.backend.entity.Notification;
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
}
