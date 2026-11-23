package org.danteplanner.backend.architecture;

import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.danteplanner.fixture.jsontree.JsonTreeFixture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTreeNavigationRuleTest {

    static final ArchRule JSON_TREES_ARE_READ_BY_PATH =
            noClasses()
                    .should().callMethodWhere(jsonNodeGet())
                    .as("main code never calls JsonNode.get(String) or get(int), which return null for a missing"
                            + " child; use path(...)");

    @Test
    void production_WhenScanned_NavigatesJsonTreesOnlyByPath() {
        assertThatCode(() -> JSON_TREES_ARE_READ_BY_PATH.check(ImportedClasses.production()))
                .doesNotThrowAnyException();
    }

    @Test
    void fixture_WhenItCallsGet_FailsNamingEachCaller() {
        String fixture = JsonTreeFixture.class.getName();

        assertThatThrownBy(() -> JSON_TREES_ARE_READ_BY_PATH.check(
                new ClassFileImporter().importClasses(JsonTreeFixture.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("use path(...)")
                .hasMessageContaining("Method <" + fixture + ".attrsOf(")
                .hasMessageContaining("Method <" + fixture + ".firstOf(")
                .hasMessageContaining("Method <" + fixture + ".typeOf(")
                .hasMessageNotContaining("Method <" + fixture + ".pathOf(");
    }

    private static DescribedPredicate<JavaMethodCall> jsonNodeGet() {
        return DescribedPredicate.describe("JsonNode.get(String) or JsonNode.get(int); use path(...)", call ->
                call.getName().equals("get")
                        && call.getTargetOwner().isAssignableTo(JsonNode.class)
                        && isKeyOrIndex(call.getTarget().getRawParameterTypes()));
    }

    private static boolean isKeyOrIndex(List<JavaClass> parameters) {
        return parameters.size() == 1
                && (parameters.get(0).isEquivalentTo(String.class) || parameters.get(0).isEquivalentTo(int.class));
    }
}
