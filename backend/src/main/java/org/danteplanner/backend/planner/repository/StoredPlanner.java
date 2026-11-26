package org.danteplanner.backend.planner.repository;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.entity.Listing;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.Takedown;
import org.danteplanner.backend.shared.dto.NotificationSetting;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

public record StoredPlanner(
        UUID id,
        Long userId,
        PlannerType plannerType,
        Instant createdAt,
        StoredContent content,
        Publication publication,
        NotificationSetting ownerNotifications,
        Listing listing,
        Takedown takedown) {

    public static final String SELECT = "new org.danteplanner.backend.planner.repository.StoredPlanner("
            + "p.id, p.userId, p.plannerType, p.createdAt, c.title, c.status, c.category, c.selectedKeywords,"
            + " c.content, c.contentSchemaVersion, c.gameContentVersion, c.syncVersion, c.deviceId,"
            + " c.lastModifiedAt, pub.published, pub.firstPublishedAt, pub.ownerNotificationsEnabled,"
            + " m.takenDownAt, m.hiddenFromRecommended, m.hiddenByModeratorId, m.hiddenReason, m.hiddenAt)";

    public static final String JOINS = "FROM Planner p JOIN PlannerContent c ON c.plannerId = p.id"
            + " JOIN PlannerPublication pub ON pub.plannerId = p.id"
            + " JOIN PlannerModeration m ON m.plannerId = p.id ";

    public record StoredContent(
            String title,
            PlannerStatus status,
            String category,
            Set<String> keywords,
            String json,
            int schemaVersion,
            int gameContentVersion,
            long syncVersion,
            @Nullable UUID deviceId,
            Instant lastModifiedAt) {

        private static StoredContent of(ContentWrite write) {
            return new StoredContent(write.title(), write.status(), write.category(), write.keywords(),
                    write.json(), write.schemaVersion(), write.gameContentVersion(), write.syncVersion(),
                    write.deviceId(), write.savedAt());
        }

        private StoredContent migrated(String migratedJson, Instant at) {
            return new StoredContent(title, status, category, keywords, migratedJson, schemaVersion,
                    gameContentVersion, syncVersion, deviceId, at.truncatedTo(ChronoUnit.MICROS));
        }
    }

    public StoredPlanner(UUID id, Long userId, PlannerType plannerType, Instant createdAt, String title,
            PlannerStatus status, String category, @Nullable Set<String> keywords, String json, int schemaVersion,
            int gameContentVersion, long syncVersion, @Nullable UUID deviceId, Instant lastModifiedAt,
            boolean published, @Nullable Instant firstPublishedAt, boolean ownerNotificationsEnabled,
            @Nullable Instant takenDownAt, boolean hiddenFromRecommended, @Nullable Long hiddenBy,
            @Nullable String hiddenReason, @Nullable Instant hiddenAt) {
        this(id, userId, plannerType, createdAt,
                new StoredContent(title, status, category, keywords == null ? Set.of() : Set.copyOf(keywords), json,
                        schemaVersion, gameContentVersion, syncVersion, deviceId, lastModifiedAt),
                published ? Publication.listed(id, firstPublishedAt) : Publication.unlisted(firstPublishedAt),
                ownerNotificationsEnabled ? NotificationSetting.ON : NotificationSetting.OFF,
                hiddenFromRecommended ? Listing.hidden(id, hiddenBy, hiddenReason, hiddenAt) : new Listing.Listed(),
                Takedown.of(takenDownAt));
    }

    public static StoredPlanner created(UUID id, Long userId, PlannerType plannerType, ContentWrite write) {
        return new StoredPlanner(id, userId, plannerType, write.savedAt(), StoredContent.of(write),
                new Publication.Draft(), NotificationSetting.ON, new Listing.Listed(), new Takedown.Clear());
    }

    public StoredPlanner saved(ContentWrite write) {
        return new StoredPlanner(id, userId, plannerType, createdAt, StoredContent.of(write), publication,
                ownerNotifications, listing, takedown);
    }

    public StoredPlanner migrated(String migratedJson, Instant at) {
        return new StoredPlanner(id, userId, plannerType, createdAt, content.migrated(migratedJson, at), publication,
                ownerNotifications, listing, takedown);
    }
}
