package org.danteplanner.backend.architecture;

import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.failure.Parsed;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsedTypeRuleTest {

    private static final String FIXTURE = "org.danteplanner.fixture.decisionpurity.ParseConventionFixtures";

    static final ArchRule PARSED_TYPES_CONSTRUCT_THEMSELVES =
            classes()
                    .should(constructNoOtherParsedType())
                    .as("a constructor of a @Parsed type is called only from that type, so its factory's check"
                            + " always ran");

    @Test
    void production_WhenScanned_ConstructsParsedTypesOnlyInThemselves() {
        assertThatCode(() -> PARSED_TYPES_CONSTRUCT_THEMSELVES.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenAParsedTypeIsConstructedElsewhere_FailsNamingEachSite() {
        assertThatThrownBy(() -> PARSED_TYPES_CONSTRUCT_THEMSELVES.check(VoidCheckRuleTest.fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(FIXTURE + "$Elsewhere.directConstruction(")
                .hasMessageContaining(FIXTURE + "$Elsewhere.referencedConstruction(")
                .hasMessageContaining(FIXTURE + "$Elsewhere.lambdaConstruction(")
                .hasMessageContaining(FIXTURE + "$Elsewhere$1.<init>(")
                .hasMessageContaining(FIXTURE + "$Subclass.<init>(")
                .hasMessageContaining(FIXTURE + "$Email$NestedBuilder.nestedConstruction(")
                .hasMessageContaining(FIXTURE + "$SealedFact is an interface")
                .hasMessageContaining("(7 times)");
    }

    private static ArchCondition<JavaClass> constructNoOtherParsedType() {
        return new ArchCondition<>("construct no other @Parsed type") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                if (origin.isInterface() && origin.isAnnotatedWith(Parsed.class)) {
                    events.add(SimpleConditionEvent.violated(origin, origin.getName()
                            + " is an interface, whose constructions no rule sees: annotate each type that holds"
                            + " the fact"));
                }
                Stream.<JavaAccess<?>>concat(
                                origin.getConstructorCallsFromSelf().stream(),
                                origin.getConstructorReferencesFromSelf().stream())
                        .filter(access -> access.getTargetOwner().isAnnotatedWith(Parsed.class))
                        .filter(access -> !access.getTargetOwner().equals(writtenIn(origin)))
                        .forEach(access -> events.add(
                                SimpleConditionEvent.violated(access, access.getDescription())));
            }
        };
    }

    private static JavaClass writtenIn(JavaClass origin) {
        return origin.isAnonymousClass() || origin.isLocalClass()
                ? origin.getEnclosingClass().map(ParsedTypeRuleTest::writtenIn).orElse(origin)
                : origin;
    }
}
