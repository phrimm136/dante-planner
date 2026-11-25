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
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import org.danteplanner.backend.shared.entity.AuditedTimestampListener;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.api.UserRole;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@EntityListeners(AuditedTimestampListener.class)
@Table(name = "users",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"provider", "providerId"})
       })
@Getter
@Builder(toBuilder = true)
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
    @Builder.Default
    private UserRole role = UserRole.NORMAL;

    @Column(name = "timeout_until")
    private @Nullable Instant timeoutUntil;

    @Column(name = "banned_at")
    private @Nullable Instant bannedAt;

    @Column(name = "banned_by")
    private @Nullable Long bannedBy;

    @PrePersist
    protected void onCreate() {
        publicId = UUID.randomUUID();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public AccountLife life() {
        return AccountLife.of(deletedAt, permanentDeleteScheduledAt);
    }
}
