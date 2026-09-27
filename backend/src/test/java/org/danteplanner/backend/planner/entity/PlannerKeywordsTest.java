package org.danteplanner.backend.planner.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerKeywordsTest {

    @Test
    void fromContent_WhenContentCarriesARenamedAndAnUnknownKeyword_RemapsAndDropsTheUnknown() {
        PlannerKeywords keywords = PlannerKeywords.fromContent(
                "{\"selectedKeywords\":[\"AccelBullet\",\"NotAKeyword\",\"Sinking\"],\"equipment\":{}}");

        assertThat(keywords.asSet()).containsExactlyInAnyOrder("9828", "Sinking");
        assertThat(keywords.dropped()).containsExactly("NotAKeyword");
    }

    @Test
    void fromContent_WhenContentHasNoSelectedKeywords_IsEmpty() {
        PlannerKeywords keywords = PlannerKeywords.fromContent("{\"equipment\":{}}");

        assertThat(keywords.isEmpty()).isTrue();
        assertThat(keywords.dropped()).isEmpty();
    }

    @Test
    void fromContent_WhenAnElementIsNotAString_DropsItEvenWhenItsDigitsNameAKeyword() {
        PlannerKeywords keywords = PlannerKeywords.fromContent("{\"selectedKeywords\":[9828,\"Burst\"]}");

        assertThat(keywords.asSet()).containsExactly("Burst");
        assertThat(keywords.dropped()).hasSize(1);
    }

    @Test
    void filterKeyword_WhenKeywordIsRenamed_ReturnsTheCurrentIdAndOtherwiseTheInput() {
        assertThat(PlannerKeywords.filterKeyword("ChargeLoad")).isEqualTo("EmergencyChargeForceField");
        assertThat(PlannerKeywords.filterKeyword("AccelBullet")).isEqualTo("9828");
        assertThat(PlannerKeywords.filterKeyword("Sinking")).isEqualTo("Sinking");
    }

    @Test
    void filterKeyword_WhenARenamedKeywordArrivesInAnotherCase_ReturnsTheCurrentId() {
        assertThat(PlannerKeywords.filterKeyword("accelbullet")).isEqualTo("9828");
        assertThat(PlannerKeywords.filterKeyword("CHARGELOAD")).isEqualTo("EmergencyChargeForceField");
    }

    @Test
    void fromContent_WhenARenamedKeywordIsStoredInAnotherCase_DropsItAsUnknown() {
        PlannerKeywords keywords = PlannerKeywords.fromContent("{\"selectedKeywords\":[\"accelbullet\"]}");

        assertThat(keywords.isEmpty()).isTrue();
        assertThat(keywords.dropped()).containsExactly("accelbullet");
    }
}
