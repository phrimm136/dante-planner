package org.danteplanner.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "user_settings")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettings {

    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Builder.Default
    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled = false;

    @Builder.Default
    @Column(name = "sync_choice_made", nullable = false)
    private boolean syncChoiceMade = false;

    @Builder.Default
    @Column(name = "notify_comments", nullable = false)
    @Setter
    private boolean notifyComments = true;

    @Builder.Default
    @Column(name = "notify_recommendations", nullable = false)
    @Setter
    private boolean notifyRecommendations = true;

    @Builder.Default
    @Column(name = "notify_new_publications", nullable = false)
    @Setter
    private boolean notifyNewPublications = false;

    /**
     * Records the user's answer to the sync prompt.
     *
     * <p>Sync can only be on once it has been chosen, an invariant the
     * {@code ck_user_settings_sync_choice} check constraint also enforces.</p>
     *
     * @param enabled whether cloud sync is enabled
     */
    public void chooseSync(boolean enabled) {
        this.syncEnabled = enabled;
        this.syncChoiceMade = true;
    }
}
