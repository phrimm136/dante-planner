package org.danteplanner.backend.architecture;

import org.springframework.data.jpa.repository.Modifying;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.lang.ArchRule;

import org.danteplanner.fixture.bulkupdate.WriteRuleFixtures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceContextClearingTest {

    static final ArchRule NO_QUERY_CLEARS_THE_CONTEXT =
            noMethods()
                    .should().beAnnotatedWith(clearingModifying())
                    .as("no @Modifying query clears the persistence context: a clear drops every pending insert or"
                            + " change it did not flush; read the changed value back with a scalar query instead");

    private static final String ROWS = WriteRuleFixtures.ClearingRows.class.getName();
    private static final String CLEARING = ROWS + ".blankClearing(java.lang.Long)";

    @Test
    void production_WhenScanned_HoldsNoClearingQuery() {
        assertThatCode(() -> NO_QUERY_CLEARS_THE_CONTEXT.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void clearingQuery_WhenScanned_FailsByNameLeavingTheFlushingOne() {
        assertThatThrownBy(() -> NO_QUERY_CLEARS_THE_CONTEXT.check(ImmutableEntityRuleTest.fixtureClasses()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(CLEARING)
                .hasMessageNotContaining("blankFlushing");
    }

    private static DescribedPredicate<JavaAnnotation<?>> clearingModifying() {
        return DescribedPredicate.describe("@Modifying(clearAutomatically = true)",
                annotation -> annotation.getRawType().isEquivalentTo(Modifying.class)
                        && Boolean.TRUE.equals(annotation.get("clearAutomatically").orElse(false)));
    }
}
