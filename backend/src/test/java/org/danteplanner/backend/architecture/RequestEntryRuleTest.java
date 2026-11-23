package org.danteplanner.backend.architecture;

import java.util.stream.Stream;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.context.request.WebRequest;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.util.RequestHeaders;
import org.danteplanner.fixture.requestentry.RequestEntryFixture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestEntryRuleTest {

    static final ArchRule REQUESTS_ARE_READ_AT_THE_ENTRY =
            classes()
                    .should(takeTheViewerAndReadHeadersThroughRequestHeaders())
                    .as("a handler takes a Viewer, never @AuthenticationPrincipal, and only RequestHeaders calls"
                            + " getHeader on a request");

    @Test
    void production_WhenScanned_ReadsRequestsAtTheEntry() {
        assertThatCode(() -> REQUESTS_ARE_READ_AT_THE_ENTRY.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenItTakesThePrincipalOrReadsAHeader_FailsNamingEachMethod() {
        String fixture = RequestEntryFixture.class.getName();

        assertThatThrownBy(() -> REQUESTS_ARE_READ_AT_THE_ENTRY.check(
                new ClassFileImporter().importClasses(RequestEntryFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(fixture + ".principalHandler(java.lang.Long) takes @AuthenticationPrincipal")
                .hasMessageContaining("Method <" + fixture + ".headerRead(")
                .hasMessageContaining("Method <" + fixture + ".wrappedHeaderRead(")
                .hasMessageContaining("Method <" + fixture + ".headerReadByReference(")
                .hasMessageContaining("Method <" + fixture + ".webRequestHeaderRead(")
                .hasMessageNotContaining("responseHeaderRead(");
    }

    private static ArchCondition<JavaClass> takeTheViewerAndReadHeadersThroughRequestHeaders() {
        return new ArchCondition<>("take the Viewer and read headers through RequestHeaders") {
            @Override
            public void check(JavaClass owner, ConditionEvents events) {
                owner.getCodeUnits().stream()
                        .filter(RequestEntryRuleTest::takesThePrincipal)
                        .forEach(unit -> events.add(SimpleConditionEvent.violated(unit,
                                unit.getFullName() + " takes @AuthenticationPrincipal")));
                if (owner.isEquivalentTo(RequestHeaders.class)) {
                    return;
                }
                Stream.<JavaAccess<?>>concat(
                                owner.getMethodCallsFromSelf().stream(), owner.getMethodReferencesFromSelf().stream())
                        .filter(RequestEntryRuleTest::readsARequestHeader)
                        .forEach(access -> events.add(
                                SimpleConditionEvent.violated(access, access.getDescription())));
            }
        };
    }

    private static boolean takesThePrincipal(JavaCodeUnit unit) {
        return unit.getParameters().stream().anyMatch(parameter ->
                parameter.isAnnotatedWith(AuthenticationPrincipal.class)
                        || parameter.isMetaAnnotatedWith(AuthenticationPrincipal.class));
    }

    private static boolean readsARequestHeader(JavaAccess<?> access) {
        return access.getName().equals("getHeader")
                && (access.getTargetOwner().isAssignableTo(HttpServletRequest.class)
                        || access.getTargetOwner().isAssignableTo(WebRequest.class));
    }
}
