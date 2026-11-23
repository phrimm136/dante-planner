package org.danteplanner.backend.planner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.converter.KeywordSetConverter;
import org.danteplanner.backend.planner.converter.PlannerStatusConverter;
import org.danteplanner.backend.planner.validation.PlannerDocument;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "planner_content")
@Getter
@NoArgsConstructor
public class PlannerContent {

    @Id
    @Column(name = "planner_id", columnDefinition = "BINARY(16)")
    private UUID plannerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "planner_id")
    @Setter
    private Planner planner;

    @Column(nullable = false)
    @Setter
    private String title = "Untitled";

    @Column(nullable = false, length = 16)
    @Setter
    @Convert(converter = PlannerStatusConverter.class)
    private PlannerStatus status = PlannerStatus.DRAFT;

    @Column(nullable = false, length = 50)
    @Setter
    private String category;

    @Column(name = "selected_keywords", columnDefinition = "JSON")
    @Setter
    @Convert(converter = KeywordSetConverter.class)
    private @Nullable Set<String> selectedKeywords;

    @Column(columnDefinition = "JSON", nullable = false)
    private String content;

    @Column(name = "content_schema_version", nullable = false)
    @Setter
    private int contentSchemaVersion = 2;

    @Column(name = "game_content_version", nullable = false)
    @Setter
    private int gameContentVersion;

    @Column(name = "sync_version", nullable = false)
    private Long syncVersion = 1L;

    @Version
    @Column(name = "row_lock_version", nullable = false)
    private Long rowLockVersion;

    @Column(name = "device_id", columnDefinition = "BINARY(16)")
    @Setter
    private @Nullable UUID deviceId;

    @Column(name = "last_modified_at", nullable = false)
    @Setter
    private Instant lastModifiedAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    /**
     * MySQL re-serializes a JSON column, so the stored form is not the string that was
     * written and a comparison made after the flush always reports a change.
     */
    @Transient
    private String loadedContent;

    @Transient
    private @Nullable Set<String> loadedSelectedKeywords;

    @Transient
    private String loadedCategory;

    public static PlannerContent of(String title, PlannerStatus status, String category, PlannerDocument document,
            int gameContentVersion, UUID deviceId) {
        PlannerContent created = new PlannerContent();
        created.title = title;
        created.status = status;
        created.category = category;
        created.selectedKeywords = document.keywords();
        created.content = document.json();
        created.gameContentVersion = gameContentVersion;
        created.deviceId = deviceId;
        return created;
    }

    @PostLoad
    protected void onLoad() {
        loadedContent = content;
        loadedSelectedKeywords = selectedKeywords;
        loadedCategory = category;
    }

    @PrePersist
    protected void onCreate() {
        if (lastModifiedAt == null) {
            lastModifiedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedAt = Instant.now();
    }

    public void replaceContent(PlannerDocument document) {
        this.content = document.json();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public void recordSave() {
        this.syncVersion = this.syncVersion + 1;
    }
}
