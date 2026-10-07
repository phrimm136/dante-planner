package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.fixturefixture.ConstructorReferenceFixture;
import org.danteplanner.fixturefixture.PlannerConstructorFixture;
import org.danteplanner.fixturefixture.SupportBuilderFixture;
import org.danteplanner.fixturefixture.UpsertRequestFixture;
import org.danteplanner.fixturefixture.UserBuilderFixture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureConstructionRuleTest {

    @Test
    void handBuiltFixtures_WhenChecked_FailNamingEach() {
        JavaClasses handBuilt = new ClassFileImporter().importClasses(
                UserBuilderFixture.class, PlannerConstructorFixture.class, UpsertRequestFixture.class);

        assertThatThrownBy(() -> FixtureConstructionRule.FIXTURES_ONLY_IN_SUPPORT.check(handBuilt))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(UserBuilderFixture.class.getName()
                        + ".user() constructs org.danteplanner.backend.user.entity.User;"
                        + " use TestDataFactory or UpsertRequests")
                .hasMessageContaining(PlannerConstructorFixture.class.getName()
                        + ".planner() constructs org.danteplanner.backend.planner.entity.Planner;"
                        + " use TestDataFactory or UpsertRequests")
                .hasMessageContaining(UpsertRequestFixture.class.getName()
                        + ".request() constructs org.danteplanner.backend.planner.dto.UpsertPlannerRequest;"
                        + " use TestDataFactory or UpsertRequests");
    }

    @Test
    void fixtureReferences_WhenChecked_FailNamingEach() {
        JavaClasses referenced = new ClassFileImporter().importClasses(ConstructorReferenceFixture.class);

        assertThatThrownBy(() -> FixtureConstructionRule.FIXTURES_ONLY_IN_SUPPORT.check(referenced))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ConstructorReferenceFixture.class.getName()
                        + ".planner() constructs org.danteplanner.backend.planner.entity.Planner;"
                        + " use TestDataFactory or UpsertRequests")
                .hasMessageContaining(ConstructorReferenceFixture.class.getName()
                        + ".user() constructs org.danteplanner.backend.user.entity.User;"
                        + " use TestDataFactory or UpsertRequests");
    }

    @Test
    void supportBuilders_WhenChecked_Pass() {
        JavaClasses throughSupport = new ClassFileImporter().importClasses(SupportBuilderFixture.class);

        assertThatCode(() -> FixtureConstructionRule.FIXTURES_ONLY_IN_SUPPORT.check(throughSupport))
                .doesNotThrowAnyException();
    }
}
