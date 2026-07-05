package org.danteplanner.backend.user.entity;

import jakarta.persistence.*;
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

    @Column(name = "sync_enabled")
    @Setter
    private Boolean syncEnabled;

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
}
