package org.danteplanner.backend.support;

import java.util.Set;
import java.util.UUID;

import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;

public final class UpsertRequests {

    private UpsertRequests() {
    }

    public static UpsertPlannerRequest valid() {
        return draft().build();
    }

    public static Builder draft() {
        return new Builder();
    }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String category = "5F";
        private String title = "Test Planner";
        private PlannerStatus status = PlannerStatus.DRAFT;
        private String content = TestDataFactory.VALID_CONTENT;
        private Integer contentVersion = TestDataFactory.mdCurrentVersion();
        private PlannerType plannerType = PlannerType.MIRROR_DUNGEON;
        private Long syncVersion;
        private Set<String> selectedKeywords;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder status(PlannerStatus status) {
            this.status = status;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder contentVersion(Integer contentVersion) {
            this.contentVersion = contentVersion;
            return this;
        }

        public Builder plannerType(PlannerType plannerType) {
            this.plannerType = plannerType;
            return this;
        }

        public Builder syncVersion(Long syncVersion) {
            this.syncVersion = syncVersion;
            return this;
        }

        public Builder selectedKeywords(Set<String> selectedKeywords) {
            this.selectedKeywords = selectedKeywords;
            return this;
        }

        public UpsertPlannerRequest build() {
            return new UpsertPlannerRequest(id, category, title, status, content, contentVersion,
                    plannerType, syncVersion, selectedKeywords);
        }
    }
}
