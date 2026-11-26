package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.converter.KeywordSetConverter;
import org.danteplanner.backend.planner.converter.PlannerStatusConverter;
import org.danteplanner.backend.planner.repository.ContentWrite;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "planner_content")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlannerContent implements Persistable<UUID> {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 16)
    @Convert(converter = PlannerStatusConverter.class)
    private PlannerStatus status;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "selected_keywords", columnDefinition = "JSON")
    @Convert(converter = KeywordSetConverter.class)
    private @Nullable Set<String> selectedKeywords;

    @Column(columnDefinition = "JSON", nullable = false)
    private String content;

    @Column(name = "content_schema_version", nullable = false)
    private int contentSchemaVersion;

    @Column(name = "game_content_version", nullable = false)
    private int gameContentVersion;

    @Column(name = "sync_version", nullable = false)
    private long syncVersion;

    @Column(name = "device_id", columnDefinition = "BINARY(16)")
    private @Nullable UUID deviceId;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    private PlannerContent(UUID plannerId, ContentWrite write) {
        this.plannerId = plannerId;
        this.title = write.title();
        this.status = write.status();
        this.category = write.category();
        this.selectedKeywords = write.keywords();
        this.content = write.json();
        this.contentSchemaVersion = write.schemaVersion();
        this.gameContentVersion = write.gameContentVersion();
        this.syncVersion = write.syncVersion();
        this.deviceId = write.device().columnValue();
        this.lastModifiedAt = write.savedAt();
    }

    public static PlannerContent created(UUID plannerId, ContentWrite write) {
        return new PlannerContent(plannerId, write);
    }

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return plannerId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }
}
