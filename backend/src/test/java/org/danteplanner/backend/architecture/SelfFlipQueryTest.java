package org.danteplanner.backend.architecture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.jpa.repository.Query;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.fixture.bulkupdate.SelfFlipQueries;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelfFlipQueryTest {

    private static final String LIST = "self-flip-queries.txt";
    private static final List<String> LINES = ShellBranches.lines(Path.of("config", LIST));
    private static final String ROWS = SelfFlipQueries.class.getName();
    private static final List<String> FLIPS = List.of("abs", "afterASubquery", "bang", "bangEqualsOne", "caseOf",
            "caseOfBoth", "caseWhen", "caseWhenNot", "equalsFalse", "ifIs", "ifOf", "metaAnnotated", "not",
            "notEqualsTrue", "notInParentheses", "notOfTheUnaliasedName", "onDuplicateKey", "oneMinus", "xor", "xorWord");

    private static final Pattern TOKEN = Pattern.compile("'(?:[^']|'')*'|[:?]?\\w+(?:\\.\\w+)*|<>|!=|[<>]=|\\S");
    private static final Set<String> ON = Set.of("true", "1");
    private static final Set<String> OFF = Set.of("false", "0");

    @Test
    void production_WhenScanned_HoldsExactlyTheListedSelfFlips() {
        assertThatCode(() -> selfFlipsAreExactly(LINES).check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void list_WhenRead_IsSortedWithoutDuplicates() {
        assertThat(LINES).isEqualTo(List.copyOf(new TreeSet<>(LINES)));
    }

    @Test
    void selfFlip_WhenNotListed_FailsByNameLeavingEveryOtherAssignment() {
        assertThatThrownBy(() -> selfFlipsAreExactly(List.of()).check(fixture()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("(" + FLIPS.size() + " times)")
                .satisfies(failure -> assertThat(FLIPS).allSatisfy(flip ->
                        assertThat(failure.getMessage()).contains("unlisted " + ROWS + "." + flip + "()")))
                .hasMessageNotContaining("allowed");
    }

    @Test
    void selfFlip_WhenListed_Passes() {
        assertThatCode(() -> selfFlipsAreExactly(FLIPS.stream().map(flip -> ROWS + "." + flip + "()").toList())
                .check(fixture()))
                .doesNotThrowAnyException();
    }

    @Test
    void listedQuery_WhenItNoLongerFlipsOrIsGone_FailsByItsLine() {
        List<String> lines = new ArrayList<>(FLIPS.stream().map(flip -> ROWS + "." + flip + "()").toList());
        lines.add("gone.Rows.gone()");
        lines.add(ROWS + ".allowedSet()");

        assertThatThrownBy(() -> selfFlipsAreExactly(lines).check(fixture()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("delete gone.Rows.gone() from backend/config/" + LIST)
                .hasMessageContaining("delete " + ROWS + ".allowedSet() from backend/config/" + LIST)
                .hasMessageContaining("(2 times)");
    }

    private static JavaClasses fixture() {
        return new ClassFileImporter().importClasses(SelfFlipQueries.class);
    }

    private static ArchRule selfFlipsAreExactly(List<String> lines) {
        return methods()
                .that(flipAColumnFromItsOwnNegation())
                .should(ImmutableEntityRuleTest.<JavaMethod>beListed(Map.of(LIST, lines), method -> false))
                .allowEmptyShould(true)
                .as("no @Query assigns a column from its own negation except exactly the lines of backend/config/"
                        + LIST + ", which only shrink: a repeated request undoes itself; write the wanted state");
    }

    private static DescribedPredicate<JavaMethod> flipAColumnFromItsOwnNegation() {
        return DescribedPredicate.describe("flip a column from its own negation", method ->
                (method.isAnnotatedWith(Query.class) || method.isMetaAnnotatedWith(Query.class))
                        && flips(AnnotatedElementUtils.findMergedAnnotation(method.reflect(), Query.class).value()));
    }

    private static boolean flips(String query) {
        return split(assignments(tokens(query)), ",").stream().anyMatch(SelfFlipQueryTest::isSelfFlip);
    }

    private static List<String> tokens(String query) {
        return TOKEN.matcher(query.replace("`", "").toLowerCase(Locale.ROOT)).results()
                .map(MatchResult::group)
                .toList();
    }

    private static List<String> assignments(List<String> query) {
        int upsert = Collections.indexOfSubList(query, List.of("on", "duplicate", "key", "update"));
        if (upsert >= 0) {
            return query.subList(upsert + 4, query.size());
        }
        if (query.isEmpty() || !query.getFirst().equals("update")) {
            return List.of();
        }
        List<List<String>> afterSet = split(query, "set");
        return afterSet.size() < 2 ? List.of() : split(afterSet.get(1), "where").getFirst();
    }

    private static List<List<String>> split(List<String> tokens, String separator) {
        List<List<String>> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token.equals("(")) {
                depth++;
            } else if (token.equals(")")) {
                depth--;
            } else if (depth == 0 && token.equals(separator)) {
                parts.add(tokens.subList(start, index));
                start = index + 1;
            }
        }
        parts.add(tokens.subList(start, tokens.size()));
        return parts;
    }

    private static boolean isSelfFlip(List<String> assignment) {
        if (assignment.size() < 3 || !assignment.get(1).equals("=")) {
            return false;
        }
        String self = column(assignment.getFirst());
        List<String> value = bare(assignment.subList(2, assignment.size()));
        return isNegation(self, value) || isConditional(self, value);
    }

    private static boolean isNegation(String self, List<String> value) {
        if (value.size() > 1 && Set.of("not", "!").contains(value.getFirst())) {
            return isSelf(self, value.subList(1, value.size()));
        }
        if (value.size() > 1 && value.getFirst().equals("abs")) {
            List<String> inner = bare(value.subList(1, value.size()));
            return isBetween(self, inner, "-", Set.of("1"));
        }
        return isBetween(self, value, "^", Set.of("1"))
                || isBetween(self, value, "xor", ON)
                || isBetween(self, value, "=", OFF)
                || isBetween(self, value, "<>", ON)
                || isBetween(self, value, "!=", ON)
                || value.size() > 2 && value.subList(0, 2).equals(List.of("1", "-"))
                        && isSelf(self, value.subList(2, value.size()));
    }

    private static boolean isConditional(String self, List<String> value) {
        if (value.isEmpty()) {
            return false;
        }
        return switch (value.getFirst()) {
            case "if" -> isIf(self, split(bare(value.subList(1, value.size())), ","));
            case "case" -> isCase(self, split(value.subList(1, value.size() - 1), "when"));
            default -> false;
        };
    }

    private static boolean isIf(String self, List<List<String>> arguments) {
        return arguments.size() == 3 && testsSelf(self, arguments.getFirst())
                && isLiteral(arguments.get(1)) && isLiteral(arguments.get(2));
    }

    private static boolean isCase(String self, List<List<String>> subjectThenBranches) {
        List<String> subject = subjectThenBranches.getFirst();
        return (subject.isEmpty() || isSelf(self, subject))
                && subjectThenBranches.stream().skip(1).allMatch(branch -> {
                    List<List<String>> testAndArms = split(branch, "then");
                    List<String> test = testAndArms.getFirst();
                    return (subject.isEmpty() ? testsSelf(self, test) : isLiteral(test))
                            && testAndArms.stream().skip(1).flatMap(arms -> split(arms, "else").stream())
                                    .allMatch(SelfFlipQueryTest::isLiteral);
                });
    }

    private static boolean testsSelf(String self, List<String> test) {
        List<String> tested = bare(test);
        if (tested.size() > 1 && Set.of("not", "!").contains(tested.getFirst())) {
            return testsSelf(self, tested.subList(1, tested.size()));
        }
        return isSelf(self, tested) || Stream.of("=", "is", "<>", "!=").anyMatch(operator ->
                isBetween(self, tested, operator, Set.of("true", "false", "1", "0")));
    }

    private static boolean isBetween(String self, List<String> value, String operator, Set<String> literals) {
        List<List<String>> sides = split(value, operator);
        return sides.size() == 2
                && (isSelf(self, sides.get(0)) && isOneOf(literals, sides.get(1))
                        || isOneOf(literals, sides.get(0)) && isSelf(self, sides.get(1)));
    }

    private static boolean isSelf(String self, List<String> tokens) {
        List<String> bare = bare(tokens);
        return bare.size() == 1 && column(bare.getFirst()).equals(self);
    }

    private static boolean isLiteral(List<String> tokens) {
        return isOneOf(ON, tokens) || isOneOf(OFF, tokens);
    }

    private static boolean isOneOf(Set<String> literals, List<String> tokens) {
        List<String> bare = bare(tokens);
        return bare.size() == 1 && literals.contains(bare.getFirst());
    }

    private static List<String> bare(List<String> tokens) {
        List<String> bare = tokens;
        while (bare.size() > 2 && bare.getFirst().equals("(") && closesOnlyAtTheEnd(bare)) {
            bare = bare.subList(1, bare.size() - 1);
        }
        return bare;
    }

    private static boolean closesOnlyAtTheEnd(List<String> tokens) {
        int depth = 0;
        for (int index = 0; index < tokens.size(); index++) {
            if (tokens.get(index).equals("(")) {
                depth++;
            } else if (tokens.get(index).equals(")")) {
                depth--;
            }
            if (depth == 0) {
                return index == tokens.size() - 1;
            }
        }
        return false;
    }

    private static String column(String reference) {
        return reference.substring(reference.lastIndexOf('.') + 1);
    }
}
