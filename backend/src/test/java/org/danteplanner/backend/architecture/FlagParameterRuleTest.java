package org.danteplanner.backend.architecture;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagParameterRuleTest {

    private static final String FIXTURE = "org.danteplanner.fixture.decisionpurity.ParseConventionFixtures";
    private static final Pattern SET_CLAUSE = Pattern.compile("(?is)\\bSET\\b(.*?)(?:\\bWHERE\\b|$)");
    private static final List<String> LISTED = ShellBranches.lines(Path.of("config/flag-parameters.txt"));
    private static final List<Class<? extends Annotation>> REQUEST_BINDINGS =
            List.of(RequestParam.class, PathVariable.class, RequestHeader.class, CookieValue.class);
    private static final DescribedPredicate<JavaMethod> FLAG_METHODS = describe("take a flag", method ->
            method.getModifiers().contains(JavaModifier.PUBLIC)
                    && takesABoolean(method)
                    && !isSetter(method)
                    && !isStoringQuery(method)
                    && !bindsRequestFlags(method)
                    && VoidCheckRuleTest.isHandWritten(method)
                    && VoidCheckRuleTest.overriddenBy(method).noneMatch(FlagParameterRuleTest::answersForItsOverrides));

    @Test
    void production_WhenScanned_HoldsExactlyTheListedFlagParameters() {
        assertThatCode(() -> flagParametersAreExactly(LISTED).check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void list_WhenRead_IsSortedWithoutDuplicates() {
        assertThat(LISTED).isSortedAccordingTo(Comparator.naturalOrder()).doesNotHaveDuplicates();
    }

    @Test
    void fixture_WhenAFlagParameterIsNotListed_FailsNamingEachSiteOnce() {
        assertThatThrownBy(() -> flagParametersAreExactly(List.of()).check(VoidCheckRuleTest.fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unlisted " + FIXTURE + "$Flagged.declaredFlag(boolean)")
                .hasMessageContaining("unlisted " + FIXTURE + "$PublicFlags.widenedFlag(boolean)")
                .hasMessageContaining("unlisted " + FIXTURE + "$PublicFlags.primitiveFlag(java.lang.String, boolean)")
                .hasMessageContaining("unlisted " + FIXTURE + "$PublicFlags.boxedFlag(java.lang.Boolean)")
                .hasMessageContaining("unlisted " + FIXTURE + "$PublicFlags.setTwo(boolean, java.lang.String)")
                .hasMessageContaining("unlisted " + FIXTURE + "$PublicFlags.settle(boolean)")
                .hasMessageContaining(
                        "unlisted " + FIXTURE + "$LibraryConsumer.accept(java.lang.Integer, java.lang.Boolean)")
                .hasMessageContaining("unlisted " + FIXTURE + "$StoredFlags.incrementVisible(long, boolean)")
                .hasMessageContaining("unlisted " + FIXTURE + "$StoredFlags.assignChosen(long, boolean)")
                .hasMessageNotContaining("assignEnabled")
                .hasMessageContaining("unlisted " + FIXTURE + "$FlagController.unboundFlag(boolean)")
                .hasMessageNotContaining("boundRequestFlag")
                .hasMessageContaining("(10 times)");
    }

    @Test
    void fixture_WhenAListedSiteIsGone_FailsNamingTheLine() {
        assertThatThrownBy(() -> flagParametersAreExactly(List.of("gone.Type.flag(boolean)"))
                .check(VoidCheckRuleTest.fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("delete gone.Type.flag(boolean)");
    }

    private static ArchRule flagParametersAreExactly(List<String> listed) {
        return methods()
                .that(FLAG_METHODS)
                .should(VoidCheckRuleTest.beExactly(listed))
                .allowEmptyShould(true)
                .as("a public method takes an enum, or is two methods, instead of a boolean; the flag parameters left"
                        + " are exactly the lines of backend/config/flag-parameters.txt, which only shrinks (a"
                        + " modifying query whose SET clause assigns the boolean is outside the rule, and so is a"
                        + " controller handler's boolean request binding, which its first line turns into a type):"
                        + " an unlisted one becomes an enum or two methods, not a line");
    }

    private static boolean takesABoolean(JavaMethod method) {
        return method.getRawParameterTypes().stream()
                .anyMatch(type -> type.isEquivalentTo(boolean.class) || type.isEquivalentTo(Boolean.class));
    }

    private static boolean bindsRequestFlags(JavaMethod method) {
        return method.getOwner().isAnnotatedWith(RestController.class)
                && Arrays.stream(method.reflect().getParameters())
                        .filter(parameter -> parameter.getType() == boolean.class
                                || parameter.getType() == Boolean.class)
                        .allMatch(parameter -> REQUEST_BINDINGS.stream().anyMatch(parameter::isAnnotationPresent));
    }

    private static boolean isStoringQuery(JavaMethod method) {
        if (!method.isAnnotatedWith(Modifying.class) || !method.isAnnotatedWith(Query.class)) {
            return false;
        }
        Matcher set = SET_CLAUSE.matcher(method.getAnnotationOfType(Query.class).value());
        return set.find() && Arrays.stream(method.reflect().getParameters())
                .filter(parameter -> parameter.getType() == boolean.class || parameter.getType() == Boolean.class)
                .allMatch(parameter -> assigns(set.group(1).trim(), nameOf(parameter)));
    }

    private static boolean assigns(String assignments, String parameter) {
        return Pattern.compile("(?:^|,)\\s*[\\w.]+\\s*=\\s*:" + Pattern.quote(parameter) + "\\s*(?:,|$)")
                .matcher(assignments)
                .find();
    }

    private static String nameOf(Parameter parameter) {
        Param param = parameter.getAnnotation(Param.class);
        return param != null ? param.value() : parameter.getName();
    }

    private static boolean isSetter(JavaMethod method) {
        return method.getName().matches("set[A-Z].*") && method.getRawParameterTypes().size() == 1;
    }

    private static boolean answersForItsOverrides(JavaMethod overridden) {
        return overridden.getModifiers().contains(JavaModifier.PUBLIC)
                || !overridden.getOwner().getPackageName().startsWith("org.danteplanner.");
    }
}
