package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.backend.integration.ExclusiveDatabaseSupport;
import org.danteplanner.exclusivedbfixture.ExclusiveMemberFixture;
import org.danteplanner.exclusivedbfixture.SharedNeighbourFixture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExclusiveDatabaseRuleTest {

    @Test
    void emptyPlannerTablesCalledFromANonMember_WhenChecked_FailsNamingTheCaller() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                ExclusiveDatabaseSupport.class, ExclusiveMemberFixture.class, SharedNeighbourFixture.class);

        assertThatThrownBy(() -> ExclusiveDatabaseRule.PLANNER_TABLES_EMPTIED_ONLY_BY_EXCLUSIVE_MEMBERS.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(SharedNeighbourFixture.class.getName()
                        + ".setUp(org.springframework.jdbc.core.JdbcTemplate) empties the planner tables outside"
                        + " ExclusiveDatabaseSupport");
    }

    @Test
    void theBaseAndAMember_WhenChecked_Pass() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                ExclusiveDatabaseSupport.class, ExclusiveMemberFixture.class);

        assertThatCode(() -> ExclusiveDatabaseRule.PLANNER_TABLES_EMPTIED_ONLY_BY_EXCLUSIVE_MEMBERS.check(classes))
                .doesNotThrowAnyException();
    }
}
