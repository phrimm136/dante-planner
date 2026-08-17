package org.danteplanner.backend.architecture.fixture;

import jakarta.persistence.Version;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/**
 * A bulk UPDATE issued over a version-checked row, held so that the rule forbidding it can be shown
 * rejecting something.
 *
 * <p>Lives in test sources, which every rule scanning production code excludes. The row carries no
 * {@code @Entity} and the repository no Spring Data supertype, so neither entity scanning nor
 * repository scanning takes them for the real thing.</p>
 */
public final class VersionedRowBulkUpdater {

    private VersionedRowBulkUpdater() {
    }

    /** A row whose writes go through an optimistic-lock check; only the version matters here. */
    public static class VersionedRow {

        @Version
        Long rowLockVersion;

        String name;
    }

    /**
     * Stands in for {@code JpaRepository}: the generic supertype naming the row a repository
     * manages, which is the only place that association is written down.
     *
     * @param <T>  the managed row
     * @param <ID> its key
     */
    public interface Rows<T, ID> {
    }

    /** The violation, beside the bulk sweep that must stay legal. */
    public interface VersionedRows extends Rows<VersionedRow, Long> {

        /** The violation itself: many rows updated in one statement, past every version check. */
        @Modifying
        @Query("UPDATE VersionedRow r SET r.name = :name WHERE r.id IN :ids")
        void renameAll(@Param("ids") Collection<Long> ids, @Param("name") String name);

        /** A sweep the rule must leave alone: a deleted row has no version left to check. */
        @Modifying
        @Query("DELETE FROM VersionedRow r WHERE r.id IN :ids")
        void deleteAllByIds(@Param("ids") Collection<Long> ids);
    }
}
