package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.privilegedroutefixture.PrivilegedHandlerFixture;
import org.danteplanner.privilegedroutefixture.StrayAdminHandlerFixture;
import org.danteplanner.privilegedroutefixture.StrayModerationHandlerFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivilegedRouteCoverageRuleTest {

    @Test
    void moderationServiceHandler_WhenMappedOutsidePrivilegedPrefixes_FailsNamingTheHandler() {
        JavaClasses stray = new ClassFileImporter().importClasses(StrayModerationHandlerFixture.class);

        assertThatThrownBy(() -> PrivilegedRouteCoverageRule.PRIVILEGED_HANDLERS_LIVE_UNDER_PRIVILEGED_PREFIXES.check(stray))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(StrayModerationHandlerFixture.class.getName() + ".unpublish(java.util.UUID)")
                        .contains("/api/fixture/planner/{id}/unpublish"));
    }

    @Test
    void adminServiceHandler_WhenMappedOutsidePrivilegedPrefixes_FailsNamingTheHandler() {
        JavaClasses stray = new ClassFileImporter().importClasses(StrayAdminHandlerFixture.class);

        assertThatThrownBy(() -> PrivilegedRouteCoverageRule.PRIVILEGED_HANDLERS_LIVE_UNDER_PRIVILEGED_PREFIXES.check(stray))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(StrayAdminHandlerFixture.class.getName() + ".role(java.lang.Long)")
                        .contains("/api/fixture/user/{targetId}/role"));
    }

    @Test
    void moderationServiceHandler_WhenMappedUnderPrivilegedPrefix_Passes() {
        JavaClasses privileged = new ClassFileImporter().importClasses(PrivilegedHandlerFixture.class);

        assertThatCode(() -> PrivilegedRouteCoverageRule.PRIVILEGED_HANDLERS_LIVE_UNDER_PRIVILEGED_PREFIXES.check(privileged))
                .doesNotThrowAnyException();
    }
}
