package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"provider", "providerId"})
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", columnDefinition = "BINARY(16)", nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String provider; // "google" or "apple"

    @Column(nullable = false)
    private String providerId; // OAuth provider's user ID

    @Column(name = "username_epithet", nullable = false, length = 50)
    private String usernameEpithet; // Epithet identifier (e.g., 'NAIVE') - user can change

    @Setter(AccessLevel.NONE) // Immutable after creation - enforces uniqueness
    @Column(name = "username_suffix", nullable = false, unique = true, length = 5)
    private String usernameSuffix; // Unique 5-character alphanumeric suffix

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "permanent_delete_scheduled_at")
    private Instant permanentDeleteScheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.NORMAL;

    @Column(name = "timeout_until")
    private Instant timeoutUntil;

    @Column(name = "banned_at")
    private Instant bannedAt;

    @Column(name = "banned_by")
    private Long bannedBy;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserSettings settings;

    @PrePersist
    protected void onCreate() {
        publicId = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Check if this user account has been soft deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete this user account and schedule permanent deletion.
     *
     * @param scheduledDeleteAt when the account should be permanently deleted
     */
    public void softDelete(Instant scheduledDeleteAt) {
        this.deletedAt = Instant.now();
        this.permanentDeleteScheduledAt = scheduledDeleteAt;
    }

    /**
     * Reactivate a soft-deleted account (during grace period).
     */
    public void reactivate() {
        this.deletedAt = null;
        this.permanentDeleteScheduledAt = null;
    }

    /**
     * Check if this user is currently timed out.
     *
     * @return true if user has an active timeout, false otherwise
     */
    public boolean isTimedOut() {
        return timeoutUntil != null && Instant.now().isBefore(timeoutUntil);
    }

    /**
     * Check if this user is currently banned.
     *
     * @return true if user is banned, false otherwise
     */
    public boolean isBanned() {
        return bannedAt != null;
    }
}
