package org.danteplanner.backend.architecture;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.restrictionfixture.BlankExemptionFixture;
import org.danteplanner.restrictionfixture.DeclaredHandlersFixture;
import org.danteplanner.restrictionfixture.DoublyDeclaredWriteHandlerFixture;
import org.danteplanner.restrictionfixture.RestrictedReadHandlerFixture;
import org.danteplanner.restrictionfixture.UndeclaredWriteHandlerFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestrictionCoverageRuleTest {

    private static final String CLASS_LEVEL_EXEMPTION = """
            package probe;

            import org.danteplanner.backend.shared.restriction.RestrictionExempt;

            @RestrictionExempt(reason = "every handler in this controller")
            public class ClassLevelExemption {
            }
            """;

    @Test
    void undeclaredWriteHandler_WhenCoverageRuleRuns_FailsNamingTheHandler() {
        JavaClasses undeclared = new ClassFileImporter().importClasses(UndeclaredWriteHandlerFixture.class);

        assertThatThrownBy(() -> RestrictionCoverageRule.WRITE_HANDLERS_DECLARE_EXACTLY_ONE_RESTRICTION.check(undeclared))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(UndeclaredWriteHandlerFixture.class.getName() + ".undeclared()")
                        .contains(UndeclaredWriteHandlerFixture.class.getName() + ".anyMethod()")
                        .contains("declares no restriction"));
    }

    @Test
    void doublyDeclaredWriteHandler_WhenCoverageRuleRuns_FailsNamingBothDeclarations() {
        JavaClasses doubly = new ClassFileImporter().importClasses(DoublyDeclaredWriteHandlerFixture.class);

        assertThatThrownBy(() -> RestrictionCoverageRule.WRITE_HANDLERS_DECLARE_EXACTLY_ONE_RESTRICTION.check(doubly))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(DoublyDeclaredWriteHandlerFixture.class.getName() + ".doublyDeclared()")
                        .contains("[@Restricted, @RestrictedByState]"));
    }

    @Test
    void declaredHandlers_WhenCoverageRuleRuns_Pass() {
        JavaClasses declared = new ClassFileImporter().importClasses(DeclaredHandlersFixture.class);

        assertThatCode(() -> RestrictionCoverageRule.WRITE_HANDLERS_DECLARE_EXACTLY_ONE_RESTRICTION.check(declared))
                .doesNotThrowAnyException();
    }

    @Test
    void blankExemption_WhenReasonRuleRuns_FailsNamingTheHandler() {
        JavaClasses blank = new ClassFileImporter().importClasses(BlankExemptionFixture.class);

        assertThatThrownBy(() -> RestrictionCoverageRule.EXEMPTIONS_NAME_A_REASON.check(blank))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(BlankExemptionFixture.class.getName() + ".blankReason()")
                        .contains("blank reason"));
    }

    @Test
    void namedExemption_WhenReasonRuleRuns_Passes() {
        JavaClasses declared = new ClassFileImporter().importClasses(DeclaredHandlersFixture.class);

        assertThatCode(() -> RestrictionCoverageRule.EXEMPTIONS_NAME_A_REASON.check(declared))
                .doesNotThrowAnyException();
    }

    @Test
    void restrictedReadHandler_WhenReadRuleRuns_FailsNamingTheHandler() {
        JavaClasses read = new ClassFileImporter().importClasses(RestrictedReadHandlerFixture.class);

        assertThatThrownBy(() -> RestrictionCoverageRule.READ_HANDLERS_DECLARE_NO_RESTRICTION.check(read))
                .isInstanceOf(AssertionError.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .contains(RestrictedReadHandlerFixture.class.getName() + ".restrictedRead()")
                        .contains("is a read handler but declares [@Restricted]"));
    }

    @Test
    void undeclaredReadHandler_WhenReadRuleRuns_Passes() {
        JavaClasses declared = new ClassFileImporter().importClasses(DeclaredHandlersFixture.class);

        assertThatCode(() -> RestrictionCoverageRule.READ_HANDLERS_DECLARE_NO_RESTRICTION.check(declared))
                .doesNotThrowAnyException();
    }

    @Test
    void classLevelExemption_WhenCompiled_IsRejectedAsNotApplicable(@TempDir Path output) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject source = new SimpleJavaFileObject(
                URI.create("string:///probe/ClassLevelExemption.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return CLASS_LEVEL_EXEMPTION;
            }
        };

        boolean compiled = compiler.getTask(null, null, diagnostics,
                List.of("-classpath", System.getProperty("java.class.path"), "-d", output.toString()),
                null, List.of(source)).call();

        assertThat(compiled).isFalse();
        assertThat(diagnostics.getDiagnostics())
                .extracting(Diagnostic::getCode)
                .contains("compiler.err.annotation.type.not.applicable");
    }
}
