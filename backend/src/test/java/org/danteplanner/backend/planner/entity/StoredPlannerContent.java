package org.danteplanner.backend.planner.entity;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import java.util.UUID;

public final class StoredPlannerContent {

    private StoredPlannerContent() {
    }

    public static PlannerContent holding(PlannerContent row, String content) {
        ReflectionTestUtils.setField(row, "content", content);
        return row;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final PlannerContent row = new PlannerContent();

        public Builder title(String title) {
            row.setTitle(title);
            return this;
        }

        public Builder status(PlannerStatus status) {
            row.setStatus(status);
            return this;
        }

        public Builder category(String category) {
            row.setCategory(category);
            return this;
        }

        public Builder selectedKeywords(Set<String> keywords) {
            row.setSelectedKeywords(keywords);
            return this;
        }

        public Builder contentSchemaVersion(int version) {
            row.setContentSchemaVersion(version);
            return this;
        }

        public Builder gameContentVersion(int version) {
            row.setGameContentVersion(version);
            return this;
        }

        public Builder syncVersion(Long version) {
            ReflectionTestUtils.setField(row, "syncVersion", version);
            return this;
        }

        public Builder deviceId(UUID deviceId) {
            row.setDeviceId(deviceId);
            return this;
        }

        public PlannerContent build() {
            return row;
        }
    }
}
