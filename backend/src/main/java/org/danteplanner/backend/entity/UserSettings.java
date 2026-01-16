package org.danteplanner.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "user_settings")
@Data
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
    private Boolean syncEnabled;

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
