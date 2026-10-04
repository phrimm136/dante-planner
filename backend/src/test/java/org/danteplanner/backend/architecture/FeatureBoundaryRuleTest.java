package org.danteplanner.backend.architecture;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.danteplanner.moduleboundaryfixture.backend.archive.service.ArchivedUserReaderFixture;
import org.danteplanner.moduleboundaryfixture.backend.comment.service.PlannerCallerFixture;
import org.danteplanner.moduleboundaryfixture.backend.comment.service.PlannerReaderFixture;
import org.danteplanner.moduleboundaryfixture.backend.moderation.controller.ModerationController;
import org.danteplanner.moduleboundaryfixture.backend.moderation.service.ModerationPolicy;
import org.danteplanner.moduleboundaryfixture.backend.moderation.service.UserModerationService;
import org.danteplanner.moduleboundaryfixture.backend.planner.entity.LabeledEnumFixture;
import org.danteplanner.moduleboundaryfixture.backend.planner.entity.LabeledRecordFixture;
import org.danteplanner.moduleboundaryfixture.backend.planner.service.AbstractFooExtensionFixture;
import org.danteplanner.moduleboundaryfixture.backend.planner.service.CommentCallerFixture;
import org.danteplanner.moduleboundaryfixture.backend.planner.service.FooImplementationFixture;
import org.danteplanner.moduleboundaryfixture.backend.planner.service.FooProviderImplementationFixture;
import org.danteplanner.moduleboundaryfixture.backend.planner.service.LabeledBeanFixture;
import org.danteplanner.moduleboundaryfixture.backend.planner.service.UserRepositoryReaderFixture;
import org.danteplanner.moduleboundaryfixture.backend.planner.service.UsersReaderFixture;
import org.danteplanner.moduleboundaryfixture.backend.shared.LabelFixture;
import org.danteplanner.moduleboundaryfixture.backend.shared.PlannerAccessReaderFixture;
import org.danteplanner.moduleboundaryfixture.backend.user.api.AbstractFoo;
import org.danteplanner.moduleboundaryfixture.backend.user.api.Foo;
import org.danteplanner.moduleboundaryfixture.backend.user.api.FooProvider;
import org.danteplanner.moduleboundaryfixture.backend.user.api.UserEntityLeakFixture;
import org.danteplanner.moduleboundaryfixture.backend.user.api.UsersApiFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeatureBoundaryRuleTest {

    @Test
    void classReachingAnotherModulesInternals_WhenScanned_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UserRepositoryReaderFixture.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.modules_meet_only_through_published_apis.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(UserRepositoryReaderFixture.class.getName())
                .hasMessageContaining("org.danteplanner.backend.user.repository.UserRepository");
    }

    @Test
    void classReachingAnotherModulesApi_WhenScanned_Passes() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UsersReaderFixture.class);

        assertThatCode(() -> FeatureBoundaryTest.modules_meet_only_through_published_apis.check(fixture))
                .doesNotThrowAnyException();
    }

    @Test
    void frozenClass_WhenItReachesAnotherModulesInternals_FailsNamingTheEdge() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ModerationController.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.modules_meet_only_through_published_apis.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ModerationController.class.getName())
                .hasMessageContaining("org.danteplanner.backend.planner.repository.PlannerRepository")
                .hasMessageNotContaining("type <org.danteplanner.backend.user.entity.User>");
    }

    @Test
    void frozenClass_WhenItReachesOnlyTheFrozenModulesInternals_Passes() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ModerationPolicy.class);

        assertThatCode(() -> FeatureBoundaryTest.modules_meet_only_through_published_apis.check(fixture))
                .doesNotThrowAnyException();
    }

    @Test
    void classInAFixtureTopLevelPackage_WhenReachingAnotherModulesInternals_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ArchivedUserReaderFixture.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.modules_meet_only_through_published_apis.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ArchivedUserReaderFixture.class.getName());
    }

    @Test
    void moduleSet_WhenDerivedFromAFixtureTopLevelPackage_IncludesIt() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ArchivedUserReaderFixture.class);

        assertThat(FeatureBoundaryTest.modulesOf(fixture)).containsExactly("archive");
    }

    @Test
    void moduleSet_WhenDerivedFromMainSources_IsTheSevenFeatures() {
        JavaClasses main = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.danteplanner.backend");

        assertThat(FeatureBoundaryTest.modulesOf(main)).isEqualTo(
                Set.of("admin", "auth", "comment", "moderation", "notification", "planner", "user"));
    }

    @Test
    void sharedClassReachingAModuleApi_WhenScanned_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(PlannerAccessReaderFixture.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.shared_depends_on_no_module.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(PlannerAccessReaderFixture.class.getName());
    }

    @Test
    void sharedClassReachingOnlyShared_WhenScanned_Passes() {
        JavaClasses fixture = new ClassFileImporter().importClasses(LabelFixture.class);

        assertThatCode(() -> FeatureBoundaryTest.shared_depends_on_no_module.check(fixture))
                .doesNotThrowAnyException();
    }

    @Test
    void modulesReachingEachOther_WhenScanned_FailNamingBothClasses() {
        JavaClasses fixture = new ClassFileImporter()
                .importClasses(CommentCallerFixture.class, PlannerCallerFixture.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.modules_are_free_of_cycles.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(CommentCallerFixture.class.getName())
                .hasMessageContaining(PlannerCallerFixture.class.getName());
    }

    @Test
    void moduleEdgeInOneDirection_WhenScanned_Passes() {
        JavaClasses fixture = new ClassFileImporter()
                .importClasses(PlannerReaderFixture.class, UsersReaderFixture.class);

        assertThatCode(() -> FeatureBoundaryTest.modules_are_free_of_cycles.check(fixture))
                .doesNotThrowAnyException();
    }

    @Test
    void frozenEntry_WhenItsFileHasNoEdge_FailsNamingTheEntry() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UsersReaderFixture.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.frozenInternalEdges_WhenScanned_AreStillLive(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("moderation.controller.ModerationController");
    }

    @Test
    void frozenEntry_WhenItsFileReachesOnlyAnotherModulesInternals_FailsNamingTheEntry() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ModerationController.class,
                org.danteplanner.moduleboundaryfixture.stale.backend.moderation.service.ModerationPolicy.class,
                UserModerationService.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.frozenInternalEdges_WhenScanned_AreStillLive(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("moderation.service.ModerationPolicy")
                .hasMessageNotContaining("moderation.controller.ModerationController")
                .hasMessageNotContaining("moderation.service.UserModerationService");
    }

    @Test
    void frozenEntries_WhenEachFileStillReachesTheFrozenModule_Pass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(
                ModerationController.class, ModerationPolicy.class, UserModerationService.class);

        assertThatCode(() -> FeatureBoundaryTest.frozenInternalEdges_WhenScanned_AreStillLive(fixture))
                .doesNotThrowAnyException();
    }

    @Test
    void apiClassReachingItsOwnModulesInternals_WhenScanned_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UserEntityLeakFixture.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.api_depends_on_no_internal_of_its_module.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(UserEntityLeakFixture.class.getName())
                .hasMessageContaining("org.danteplanner.backend.user.entity.User");
    }

    @Test
    void apiClassReachingItsOwnModulesApi_WhenScanned_Passes() {
        JavaClasses fixture = new ClassFileImporter().importClasses(UsersApiFixture.class);

        assertThatCode(() -> FeatureBoundaryTest.api_depends_on_no_internal_of_its_module.check(fixture))
                .doesNotThrowAnyException();
    }

    @Test
    void unlabeledInterfaceWithoutRoleWord_WhenImplementedOutsideItsModule_FailsNamingTheInterface() {
        JavaClasses fixture = new ClassFileImporter().importClasses(Foo.class, FooImplementationFixture.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.interfaces_implemented_across_modules_name_their_role.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(Foo.class.getName())
                .hasMessageContaining(FooImplementationFixture.class.getName());
    }

    @Test
    void roleWordLabelOrAbstractClass_WhenImplementedOutsideItsModule_Passes() {
        JavaClasses fixture = new ClassFileImporter().importClasses(
                FooProvider.class, FooProviderImplementationFixture.class,
                LabelFixture.class, LabeledRecordFixture.class,
                AbstractFoo.class, AbstractFooExtensionFixture.class);

        assertThatCode(() -> FeatureBoundaryTest.interfaces_implemented_across_modules_name_their_role.check(fixture))
                .doesNotThrowAnyException();
    }

    @Test
    void beanClass_WhenItImplementsATypeLabel_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(LabelFixture.class, LabeledBeanFixture.class);

        assertThatThrownBy(() -> FeatureBoundaryTest.type_labels_are_implemented_only_by_data_types.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(LabeledBeanFixture.class.getName());
    }

    @Test
    void recordOrEnumWithConstantBodies_WhenItImplementsATypeLabel_Passes() {
        JavaClasses fixture = new ClassFileImporter().importClasses(LabelFixture.class, LabeledRecordFixture.class,
                LabeledEnumFixture.class, LabeledEnumFixture.WITH_BODY.getClass());

        assertThatCode(() -> FeatureBoundaryTest.type_labels_are_implemented_only_by_data_types.check(fixture))
                .doesNotThrowAnyException();
    }
}
