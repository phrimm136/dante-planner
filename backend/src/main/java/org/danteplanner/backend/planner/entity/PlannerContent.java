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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.danteplanner.backend.planner.converter.KeywordSetConverter;
import org.danteplanner.backend.planner.converter.PlannerStatusConverter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owner-mutated planner content row. The only writer is the owning user's save path,
 * making {@code row_lock_version} the aggregate's single optimistic-lock boundary.
 * Deliberately bare: no secondary index, no inbound FK (INV6), so a concurrent
 * same-row write can only serialize on the row's X lock — never deadlock via a
 * child's shared lock.
 */
@Entity
@Table(name = "planner_content")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
    @Builder.Default
    private String title = "Untitled";

    @Column(nullable = false, length = 16)
    @Setter
    @Convert(converter = PlannerStatusConverter.class)
    @Builder.Default
    private PlannerStatus status = PlannerStatus.DRAFT;

    @Column(nullable = false, length = 50)
    @Setter
    private String category;

    @Column(name = "selected_keywords", columnDefinition = "JSON")
    @Setter
    @Convert(converter = KeywordSetConverter.class)
    private Set<String> selectedKeywords;

    @Column(columnDefinition = "JSON", nullable = false)
    @Setter
    private String content;

    /**
     * SHA-256 over the content document as the write path received it. MySQL re-serializes a JSON
     * column, so this is the only surviving identity of the author's bytes: recomputing it from
     * {@link #content} after a round trip yields a different value for an unchanged document.
     */
    @Column(name = "content_digest", columnDefinition = "BINARY(32)", nullable = false)
    private byte[] contentDigest;

    @Column(name = "content_schema_version", nullable = false)
    @Setter
    @Builder.Default
    private int contentSchemaVersion = 2;

    @Column(name = "game_content_version", nullable = false)
    @Setter
    private int gameContentVersion;

    @Column(name = "sync_version", nullable = false)
    @Builder.Default
    private Long syncVersion = 1L;

    @Version
    @Column(name = "row_lock_version", nullable = false)
    private Long rowLockVersion;

    @Column(name = "device_id", columnDefinition = "BINARY(16)")
    @Setter
    private UUID deviceId;

    @Column(name = "last_modified_at", nullable = false)
    @Setter
    private Instant lastModifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * The searchable values as this row was read. MySQL re-serializes a JSON column,
     * so the stored form is not the string that was written and a comparison made
     * after the flush always reports a change; only a value captured at load answers
     * whether the transaction actually moved the document or the keyword set.
     */
    @Transient
    private String loadedContent;

    @Transient
    private Set<String> loadedSelectedKeywords;

    @PostLoad
    protected void onLoad() {
        loadedContent = content;
        loadedSelectedKeywords = selectedKeywords;
    }

    @PrePersist
    protected void onCreate() {
        if (lastModifiedAt == null) {
            lastModifiedAt = Instant.now();
        }
        contentDigest = digestOf(content);
    }

    private static byte[] digestOf(String content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String contentDigestHex() {
        return HexFormat.of().formatHex(contentDigest);
    }

    @PreUpdate
    protected void onUpdate() {
        lastModifiedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete: stamped here (not on the core row) so cross-device sync pulls
     * see the deletion without a join.
     */
    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    /**
     * Record a save: bump the sync version handed back to clients, and re-derive the content
     * digest when this transaction actually moved the document.
     */
    public void recordSave() {
        this.syncVersion = this.syncVersion + 1;
        if (!Objects.equals(content, loadedContent)) {
            this.contentDigest = digestOf(content);
        }
    }
}
