package org.danteplanner.backend.support;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.user.api.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UpsertRequestsTest {

    private static final String STORED_CONTENT = "{\"selectedKeywords\":[],\"sectionNotes\":{}}";

    @Test
    void valid_WhenCheckedByTheCreateVersionRule_IsAccepted() {
        GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(new ObjectMapper()), "../static/data");
        registry.init();
        UpsertPlannerRequest request = UpsertRequests.valid();

        assertThatCode(() -> new ContentVersionValidator(registry)
                .validateVersionForCreate(request.plannerType(), request.contentVersion()))
                .doesNotThrowAnyException();
    }

    @Test
    void valid_WhenCalledTwice_IssuesDistinctIds() {
        assertThat(UpsertRequests.valid().id()).isNotEqualTo(UpsertRequests.valid().id());
    }

    @Test
    void draft_WhenOneFieldIsVaried_KeepsEveryOtherDefault() {
        UpsertPlannerRequest base = UpsertRequests.draft().id("fixed").build();

        UpsertPlannerRequest varied = UpsertRequests.draft().id("fixed").title("Other").build();

        assertThat(varied).usingRecursiveComparison().ignoringFields("title").isEqualTo(base);
        assertThat(varied.title()).isEqualTo("Other");
    }

    @Test
    void resending_WhenBuilt_CarriesThePlannerId() {
        Planner planner = stored();

        assertThat(UpsertRequests.resending(planner).build().id()).isEqualTo(planner.getId().toString());
    }

    @Test
    void resending_WhenBuilt_CarriesThePlannerCategory() {
        assertThat(UpsertRequests.resending(stored()).build().category()).isEqualTo("10F");
    }

    @Test
    void resending_WhenBuilt_CarriesThePlannerTitle() {
        assertThat(UpsertRequests.resending(stored()).build().title()).isEqualTo("T");
    }

    @Test
    void resending_WhenBuilt_CarriesTheStoredContent() {
        assertThat(UpsertRequests.resending(stored()).build().content()).isEqualTo(STORED_CONTENT);
    }

    @Test
    void resending_WhenBuilt_CarriesTheStoredContentVersion() {
        assertThat(UpsertRequests.resending(stored()).build().contentVersion()).isEqualTo(5);
    }

    @Test
    void resending_WhenBuilt_CarriesThePlannerType() {
        assertThat(UpsertRequests.resending(stored()).build().plannerType()).isEqualTo(PlannerType.REFRACTED_RAILWAY);
    }

    @Test
    void resending_WhenBuilt_CarriesTheStoredSyncVersion() {
        assertThat(UpsertRequests.resending(stored()).build().syncVersion()).isEqualTo(4L);
    }

    @Test
    void resending_WhenBuilt_LeavesStatusNull() {
        assertThat(UpsertRequests.resending(stored()).build().status()).isNull();
    }

    @Test
    void resending_WhenBuilt_LeavesSelectedKeywordsNull() {
        assertThat(UpsertRequests.resending(stored()).build().selectedKeywords()).isNull();
    }

    private static Planner stored() {
        return TestDataFactory.planner(TestDataFactory.unsavedUser(UserRole.NORMAL))
                .category("10F")
                .title("T")
                .content(STORED_CONTENT)
                .contentVersion(5)
                .plannerType(PlannerType.REFRACTED_RAILWAY)
                .syncVersion(4)
                .build();
    }
}
