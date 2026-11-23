package org.danteplanner.backend.architecture;

import java.lang.annotation.Annotation;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.http.HttpEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.WebRequest;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaParameter;
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

    private static final Set<String> REQUEST_READS = Set.of(
            "getHeader", "getHeaders", "getHeaderNames", "getHeaderValues", "getIntHeader", "getDateHeader",
            "getUserPrincipal", "getRemoteUser", "getPrincipal");
    private static final List<Class<?>> REQUEST_TYPES =
            List.of(HttpServletRequest.class, ServerHttpRequest.class, WebRequest.class);
    private static final List<Class<? extends Annotation>> BANNED_ANNOTATIONS =
            List.of(AuthenticationPrincipal.class, CurrentSecurityContext.class, RequestHeader.class);
    private static final List<Class<?>> BANNED_TYPES = List.of(Principal.class, HttpEntity.class);

    static final ArchRule REQUESTS_ARE_READ_AT_THE_ENTRY =
            classes()
                    .should(takeTheViewerAndReadHeadersThroughRequestHeaders())
                    .as("a method takes a Viewer, never another form of the signed-in user or of the request"
                            + " headers, and only RequestHeaders reads a header or the user from a request");

    @Test
    void production_WhenScanned_ReadsRequestsAtTheEntry() {
        assertThatCode(() -> REQUESTS_ARE_READ_AT_THE_ENTRY.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenItTakesThePrincipalOrReadsAHeader_FailsNamingEachMethod() {
        String fixture = RequestEntryFixture.class.getName();

        assertThatThrownBy(() -> REQUESTS_ARE_READ_AT_THE_ENTRY.check(
                new ClassFileImporter().importClasses(
                        RequestEntryFixture.class, RequestEntryFixture.Advice.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(fixture + ".principalHandler(java.lang.Long) takes")
                .hasMessageContaining(fixture + ".composedPrincipalHandler(")
                .hasMessageContaining(fixture + "$Advice.principalAdvice(")
                .hasMessageContaining(fixture + ".securityContextHandler(")
                .hasMessageContaining(fixture + ".authenticationHandler(")
                .hasMessageContaining(fixture + ".javaPrincipalHandler(")
                .hasMessageContaining(fixture + ".headerHandler(")
                .hasMessageContaining(fixture + ".httpEntityHandler(")
                .hasMessageContaining(fixture + ".requestEntityHandler(")
                .hasMessageContaining("Method <" + fixture + ".headerValuesRead(")
                .hasMessageContaining("Method <" + fixture + ".headerNamesRead(")
                .hasMessageContaining("Method <" + fixture + ".intHeaderRead(")
                .hasMessageContaining("Method <" + fixture + ".dateHeaderRead(")
                .hasMessageContaining("Method <" + fixture + ".serverRequestHeadersRead(")
                .hasMessageContaining("Method <" + fixture + ".webRequestHeaderValuesRead(")
                .hasMessageContaining("Method <" + fixture + ".headerRead(")
                .hasMessageContaining("Method <" + fixture + ".wrappedHeaderRead(")
                .hasMessageContaining("Method <" + fixture + ".headerReadByReference(")
                .hasMessageContaining("Method <" + fixture + ".webRequestHeaderRead(")
                .hasMessageContaining("Method <" + fixture + ".userPrincipalRead(")
                .hasMessageContaining("Method <" + fixture + ".remoteUserRead(")
                .hasMessageContaining("Method <" + fixture + ".serverRequestPrincipalRead(")
                .hasMessageNotContaining("viewerHandler(")
                .hasMessageNotContaining("authenticationOutsideAController(")
                .hasMessageNotContaining("responseHeaderRead(");
    }

    private static ArchCondition<JavaClass> takeTheViewerAndReadHeadersThroughRequestHeaders() {
        return new ArchCondition<>("take the Viewer and read headers through RequestHeaders") {
            @Override
            public void check(JavaClass owner, ConditionEvents events) {
                owner.getMethods().stream()
                        .forEach(method -> method.getParameters().stream()
                                .filter(parameter -> bypassesTheEntry(owner, parameter))
                                .forEach(parameter -> events.add(SimpleConditionEvent.violated(method,
                                        method.getFullName() + " takes " + parameter.getDescription()))));
                if (owner.isEquivalentTo(RequestHeaders.class)) {
                    return;
                }
                Stream.<JavaAccess<?>>concat(
                                owner.getMethodCallsFromSelf().stream(), owner.getMethodReferencesFromSelf().stream())
                        .filter(RequestEntryRuleTest::readsTheRequest)
                        .forEach(access -> events.add(
                                SimpleConditionEvent.violated(access, access.getDescription())));
            }
        };
    }

    private static boolean bypassesTheEntry(JavaClass owner, JavaParameter parameter) {
        return BANNED_ANNOTATIONS.stream().anyMatch(annotation ->
                        parameter.isAnnotatedWith(annotation) || parameter.isMetaAnnotatedWith(annotation))
                || owner.isMetaAnnotatedWith(Controller.class)
                        && BANNED_TYPES.stream().anyMatch(parameter.getRawType()::isAssignableTo);
    }

    private static boolean readsTheRequest(JavaAccess<?> access) {
        return REQUEST_READS.contains(access.getName())
                && REQUEST_TYPES.stream().anyMatch(access.getTargetOwner()::isAssignableTo);
    }
}
