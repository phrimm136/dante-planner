package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "planner_publication")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlannerPublication {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "planner_id")
    @Setter
    private Planner planner;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "first_published_at")
    @Setter
    private @Nullable Instant firstPublishedAt;

    @Column(name = "owner_notifications_enabled", nullable = false)
    @Setter(AccessLevel.PACKAGE)
    @Builder.Default
    private boolean ownerNotificationsEnabled = true;

    public sealed interface FirstPublished {

        record Never() implements FirstPublished {
        }

        record At(Instant instant) implements FirstPublished {
        }

        static FirstPublished of(@Nullable Instant column) {
            return column == null ? new Never() : new At(column);
        }
    }

    public record Transition(boolean published, FirstPublished firstPublished, PublicationOutcome outcome) {
    }

    public static Transition publish(boolean published, FirstPublished firstPublished, Instant now) {
        if (published) {
            return new Transition(true, firstPublished, PublicationOutcome.NONE);
        }
        return switch (firstPublished) {
            case FirstPublished.Never never ->
                    new Transition(true, new FirstPublished.At(now), PublicationOutcome.FIRST_PUBLISH);
            case FirstPublished.At at -> new Transition(true, at, PublicationOutcome.REPUBLISH);
        };
    }

    public static Transition unpublish(boolean published, FirstPublished firstPublished) {
        return new Transition(false, firstPublished,
                published ? PublicationOutcome.WITHDRAWN : PublicationOutcome.NONE);
    }

    public static Transition takeDown(boolean published, FirstPublished firstPublished, boolean moderated) {
        return new Transition(false, firstPublished,
                published || moderated ? PublicationOutcome.WITHDRAWN : PublicationOutcome.NONE);
    }

    PublicationOutcome publish(Instant now) {
        return assign(publish(published, FirstPublished.of(firstPublishedAt), now));
    }

    PublicationOutcome unpublish() {
        return assign(unpublish(published, FirstPublished.of(firstPublishedAt)));
    }

    PublicationOutcome takeDown(boolean moderated) {
        return assign(takeDown(published, FirstPublished.of(firstPublishedAt), moderated));
    }

    private PublicationOutcome assign(Transition transition) {
        this.published = transition.published();
        this.firstPublishedAt = switch (transition.firstPublished()) {
            case FirstPublished.Never never -> null;
            case FirstPublished.At at -> at.instant();
        };
        return transition.outcome();
    }
}
