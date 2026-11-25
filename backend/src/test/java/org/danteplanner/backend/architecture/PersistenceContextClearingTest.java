package org.danteplanner.backend.architecture;

import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.Modifying;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.fixture.bulkupdate.WriteRuleFixtures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceContextClearingTest {

    private static final String LIST_PREFIX = "context-clearing-";
    private static final String ROWS = WriteRuleFixtures.ClearingRows.class.getName();
    private static final String CLEARING = ROWS + ".blankClearing(java.lang.Long)";
    private static final String FLUSHING = ROWS + ".blankFlushing(java.lang.Long)";
    private static final String LIST = LIST_PREFIX + "bulkupdate.txt";

    @Test
    void production_WhenScanned_HoldsExactlyTheListedClearingQueries() {
        assertThatCode(() -> clearingQueriesAreExactly(ImmutableEntityRuleTest.moduleLists(LIST_PREFIX))
                .check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void lists_WhenRead_AreSortedWithoutDuplicatesAndHoldOnlyTheirModule() {
        assertThat(ImmutableEntityRuleTest.defectsIn(
                ImmutableEntityRuleTest.moduleLists(LIST_PREFIX), LIST_PREFIX)).isEmpty();
    }

    @Test
    void clearingQuery_WhenNotListed_FailsByNameLeavingTheFlushingOne() {
        assertThatThrownBy(() -> clearingQueriesAreExactly(Map.of()).check(ImmutableEntityRuleTest.fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unlisted " + CLEARING)
                .hasMessageNotContaining("blankFlushing");
    }

    @Test
    void clearingQuery_WhenListed_Passes() {
        assertThatCode(() -> clearingQueriesAreExactly(Map.of(LIST, List.of(CLEARING)))
                .check(ImmutableEntityRuleTest.fixtureClasses()))
                .doesNotThrowAnyException();
    }

    @Test
    void listedQuery_WhenItNoLongerClearsOrIsGone_FailsByItsLine() {
        assertThatThrownBy(() -> clearingQueriesAreExactly(Map.of(LIST, List.of("gone.Rows.gone()", CLEARING, FLUSHING)))
                .check(ImmutableEntityRuleTest.fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("delete " + FLUSHING + " from backend/config/" + LIST)
                .hasMessageContaining("delete gone.Rows.gone() from backend/config/" + LIST)
                .hasMessageContaining("(2 times)");
    }

    private static ArchRule clearingQueriesAreExactly(Map<String, List<String>> lists) {
        return methods()
                .that().areAnnotatedWith(clearingModifying())
                .should(ImmutableEntityRuleTest.<JavaMethod>beListed(lists, method -> false))
                .allowEmptyShould(true)
                .as("no @Modifying query clears the persistence context except exactly the lines of backend/config/"
                        + LIST_PREFIX + "MODULE.txt, which only shrink: a clear drops every pending insert or change"
                        + " it did not flush; read the changed value back with a scalar query instead");
    }

    private static DescribedPredicate<JavaAnnotation<?>> clearingModifying() {
        return DescribedPredicate.describe("@Modifying(clearAutomatically = true)",
                annotation -> annotation.getRawType().isEquivalentTo(Modifying.class)
                        && Boolean.TRUE.equals(annotation.get("clearAutomatically").orElse(false)));
    }
}
