package org.danteplanner.backend.architecture;

import java.util.Set;

import org.springframework.data.repository.CrudRepository;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps {@code repository.save()} inside repository interfaces.
 *
 * <p>A new row goes through the {@code insert()} default method that names it as one; a mutation of
 * an entity already loaded in the transaction is written by dirty checking at commit, with no call
 * at all. A {@code save()} in a service is therefore either a creation wearing the wrong name or a
 * redundant merge over the mechanism actually doing the work, and neither is distinguishable from
 * the other at the call site — which is why the ban is total rather than conditional.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceWriteStyleTest {

    private static final Set<String> WRITE_METHODS = Set.of("save", "saveAll", "saveAndFlush");

    @ArchTest
    static final ArchRule save_is_confined_to_repositories =
            noClasses()
                    .that().resideOutsideOfPackage("..repository..")
                    .should().callMethodWhere(isSpringDataWrite())
                    .as("save/saveAll/saveAndFlush is callable only from within a repository: "
                            + "new rows go through insert(), mutations through dirty checking");

    private static DescribedPredicate<JavaMethodCall> isSpringDataWrite() {
        return new DescribedPredicate<>("a Spring Data repository write") {
            @Override
            public boolean test(JavaMethodCall call) {
                return WRITE_METHODS.contains(call.getTarget().getName())
                        && call.getTargetOwner().isAssignableTo(CrudRepository.class);
            }
        };
    }
}
