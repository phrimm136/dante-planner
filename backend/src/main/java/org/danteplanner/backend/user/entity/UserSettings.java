package org.danteplanner.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "user_settings")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Builder.Default
    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled = false;

    @Builder.Default
    @Column(name = "sync_choice_made", nullable = false)
    private boolean syncChoiceMade = false;

    @Builder.Default
    @Column(name = "notify_comments", nullable = false)
    private boolean notifyComments = true;

    @Builder.Default
    @Column(name = "notify_recommendations", nullable = false)
    private boolean notifyRecommendations = true;

    @Builder.Default
    @Column(name = "notify_new_publications", nullable = false)
    private boolean notifyNewPublications = false;
}
