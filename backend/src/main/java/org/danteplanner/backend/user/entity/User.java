package org.danteplanner.backend.user.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.danteplanner.backend.auth.converter.AuthProviderTypeConverter;
import org.danteplanner.backend.auth.entity.AuthProviderType;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
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
    @Setter
    @Builder.Default
    private UserRole role = UserRole.NORMAL;

    @Column(name = "timeout_until")
    @Setter
    private Instant timeoutUntil;

    @Column(name = "banned_at")
    @Setter
    private Instant bannedAt;

    @Column(name = "banned_by")
    @Setter
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(Instant scheduledDeleteAt) {
        this.deletedAt = Instant.now();
        this.permanentDeleteScheduledAt = scheduledDeleteAt;
    }

    public void reactivate() {
        this.deletedAt = null;
        this.permanentDeleteScheduledAt = null;
    }

    public RestrictionState restrictionState(Clock clock) {
        if (timeoutUntil != null && clock.instant().isBefore(timeoutUntil)) {
            return RestrictionState.TIMED_OUT;
        }
        return bannedAt != null ? RestrictionState.BANNED : RestrictionState.ACTIVE;
    }

    public boolean isTimedOut() {
        return restrictionState(Clock.systemUTC()) == RestrictionState.TIMED_OUT;
    }

    public boolean isBanned() {
        return bannedAt != null;
    }
}
