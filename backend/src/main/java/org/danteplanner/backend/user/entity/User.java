package org.danteplanner.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import org.danteplanner.backend.shared.entity.AuditedTimestampListener;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.api.RestrictionState;
import org.danteplanner.backend.user.api.UserRole;

import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(AuditedTimestampListener.class)
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"provider", "providerId"})
       })
@Getter
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

    @Convert(converter = AuthProviderTypeConverter.class)
    @Column(nullable = false)
    private AuthProviderType provider; // "google" or "apple"

    @Column(nullable = false)
    private String providerId; // OAuth provider's user ID

    @Column(name = "username_epithet", nullable = false, length = 50)
    @Setter
    private String usernameEpithet; // Epithet identifier (e.g., 'NAIVE') - user can change

    @Column(name = "username_suffix", nullable = false, unique = true, length = 5)
    private String usernameSuffix; // Unique 5-character alphanumeric suffix

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @Column(name = "permanent_delete_scheduled_at")
    private @Nullable Instant permanentDeleteScheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    @Builder.Default
    private UserRole role = UserRole.NORMAL;

    @Column(name = "timeout_until")
    @Setter
    private @Nullable Instant timeoutUntil;

    @Column(name = "banned_at")
    @Setter
    private @Nullable Instant bannedAt;

    @Column(name = "banned_by")
    @Setter
    private @Nullable Long bannedBy;

    @PrePersist
    protected void onCreate() {
        publicId = UUID.randomUUID();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Instant requirePermanentDeleteScheduledAt() {
        if (permanentDeleteScheduledAt == null) {
            throw new IllegalStateException("user " + id + " is deleted without a permanent deletion time");
        }
        return permanentDeleteScheduledAt;
    }

    public void softDelete(Instant now, Instant scheduledDeleteAt) {
        this.deletedAt = now;
        this.permanentDeleteScheduledAt = scheduledDeleteAt;
    }

    public void reactivate() {
        this.deletedAt = null;
        this.permanentDeleteScheduledAt = null;
    }

    public RestrictionState restrictionState(Instant now) {
        if (timeoutUntil != null && now.isBefore(timeoutUntil)) {
            return RestrictionState.TIMED_OUT;
        }
        return bannedAt != null ? RestrictionState.BANNED : RestrictionState.ACTIVE;
    }

    public boolean isTimedOut(Instant now) {
        return restrictionState(now) == RestrictionState.TIMED_OUT;
    }

    public boolean isBanned() {
        return bannedAt != null;
    }
}
