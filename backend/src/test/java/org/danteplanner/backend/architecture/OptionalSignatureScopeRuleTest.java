package org.danteplanner.backend.architecture;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionalSignatureScopeRuleTest {

    private static final String ROOT = "org.danteplanner.backend";
    private static final Set<String> MODULES = Set.of("user", "auth");
    private static final String FIXTURE_ROOT = "org.danteplanner.fixture.optionalsignature";
    private static final String FIXTURE = FIXTURE_ROOT + ".scoped.OptionalSignatureFixture";

    @Test
    void production_WhenScanned_KeepsOptionalOutOfTheScopedModulesSignatures() {
        assertThatCode(() -> optionalSignaturesAreAbsent(ROOT, MODULES).check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenAScopedSignatureCarriesOptional_FailsNamingEachSite() {
        assertThatThrownBy(() -> optionalSignaturesAreAbsent(FIXTURE_ROOT, Set.of("scoped"))
                .check(new ClassFileImporter().importPackages(FIXTURE_ROOT)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(FIXTURE + ".returned() returns java.util.Optional")
                .hasMessageContaining(FIXTURE + ".taken(java.util.Optional) takes java.util.Optional")
                .hasMessageContaining(FIXTURE + ".nested() returns java.util.Optional")
                .hasMessageContaining(FIXTURE + ".held holds java.util.Optional")
                .hasMessageContaining(FIXTURE + ".<init>(java.util.Optional) takes java.util.Optional")
                .hasMessageNotContaining("OptionalSignatureFixtureRepository")
                .hasMessageNotContaining("OutOfScope")
                .hasMessageNotContaining("lambda$")
                .hasMessageContaining("(5 times)");
    }

    private static ArchRule optionalSignaturesAreAbsent(String root, Set<String> modules) {
        return classes()
                .that(inScope(root, modules))
                .should(carryNoOptionalInASignatureOrField())
                .allowEmptyShould(true)
                .as(("in the modules %s, no method returns or takes java.util.Optional and no field holds one, "
                        + "inside a generic too; a repository hands its Optional over and the line receiving it "
                        + "turns it into a type").formatted(modules));
    }

    private static DescribedPredicate<JavaClass> inScope(String root, Set<String> modules) {
        return describe("are in a scoped module outside its repositories", type -> {
            String name = type.getPackageName();
            return modules.stream().anyMatch(module -> name.equals(root + "." + module)
                            || name.startsWith(root + "." + module + "."))
                    && !Arrays.asList(name.split("\\.")).contains("repository");
        });
    }

    private static ArchCondition<JavaClass> carryNoOptionalInASignatureOrField() {
        return new ArchCondition<>("carry no Optional in a signature or field") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                for (JavaCodeUnit unit : type.getCodeUnits()) {
                    if (unit.getModifiers().contains(JavaModifier.SYNTHETIC)) {
                        continue;
                    }
                    if (unit instanceof JavaMethod method && involvesOptional(method.getReturnType())) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " returns " + Optional.class.getName()));
                    }
                    if (unit.getParameterTypes().stream().anyMatch(OptionalSignatureScopeRuleTest::involvesOptional)) {
                        events.add(SimpleConditionEvent.violated(unit,
                                unit.getFullName() + " takes " + Optional.class.getName()));
                    }
                }
                for (JavaField field : type.getFields()) {
                    if (!field.getModifiers().contains(JavaModifier.SYNTHETIC) && involvesOptional(field.getType())) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getFullName() + " holds " + Optional.class.getName()));
                    }
                }
            }
        };
    }

    private static boolean involvesOptional(JavaType type) {
        return Stream.of(type).flatMap(involved -> involved.getAllInvolvedRawTypes().stream())
                .anyMatch(raw -> raw.isEquivalentTo(Optional.class));
    }
}
