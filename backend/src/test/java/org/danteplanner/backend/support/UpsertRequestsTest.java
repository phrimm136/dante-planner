package org.danteplanner.backend.support;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UpsertRequestsTest {

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
}
