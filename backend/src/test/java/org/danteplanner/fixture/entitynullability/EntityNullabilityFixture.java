package org.danteplanner.fixture.entitynullability;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;

import org.jspecify.annotations.Nullable;

@Entity
public class EntityNullabilityFixture {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "edited_at")
    private @Nullable Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "archived_at", nullable = false)
    private @Nullable Instant archivedAt;

    @OneToMany
    private List<EntityNullabilityFixture> children;

    @Transient
    private String derived;

    private int count;
}
