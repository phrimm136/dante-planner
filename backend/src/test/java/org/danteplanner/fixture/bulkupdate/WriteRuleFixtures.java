package org.danteplanner.fixture.bulkupdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import lombok.Setter;

import org.hibernate.annotations.Immutable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class WriteRuleFixtures {

    private WriteRuleFixtures() {
    }

    @Entity
    public static class MutableRow {

        @Id
        private Long id;

        @Column(nullable = false)
        private String title;

        public void rename(String newTitle) {
            title = newTitle;
        }
    }

    @Entity
    static class PackagePrivateMutableRow {

        @Id
        private Long id;
    }

    @Entity
    @LookalikeImmutable.Immutable
    public static class LookalikeRow {

        @Id
        private Long id;
    }

    @Entity
    @Immutable
    public static class LifecycleRow {

        private static final Object SHARED = new Object();

        @Id
        private Long id;

        @Column(nullable = false)
        private String title;

        boolean isNew = true;

        protected LifecycleRow() {
        }

        public LifecycleRow(String title) {
            this.title = title;
        }

        @PrePersist
        void fillTitle() {
            title = SHARED.toString();
        }

        @PostPersist
        void markPersisted() {
            isNew = false;
        }

        @PostLoad
        void markLoaded() {
            isNew = false;
        }
    }

    @Entity
    @Immutable
    public static class WrittenRow {

        @Id
        private Long id;

        @Setter
        @Column(nullable = false)
        private String title;

        @Column(nullable = false)
        String note;

        public void rename(String newTitle) {
            title = newTitle;
        }
    }

    public static class Neighbour {

        public Neighbour(WrittenRow row) {
            row.note = "from a foreign constructor";
        }

        public void annotate(WrittenRow row) {
            row.note = "from a foreign method";
        }

        @PrePersist
        public void stamp(WrittenRow row) {
            row.note = "from a foreign lifecycle method";
        }
    }

    public interface ClearingRows {

        @Modifying(clearAutomatically = true)
        @Query("UPDATE MutableRow r SET r.title = '' WHERE r.id = :id")
        int blankClearing(Long id);

        @Modifying(flushAutomatically = true)
        @Query("UPDATE MutableRow r SET r.title = '' WHERE r.id = :id")
        int blankFlushing(Long id);
    }

    static final class LookalikeImmutable {

        private LookalikeImmutable() {
        }

        @Target(ElementType.TYPE)
        @Retention(RetentionPolicy.RUNTIME)
        @interface Immutable {
        }
    }
}
