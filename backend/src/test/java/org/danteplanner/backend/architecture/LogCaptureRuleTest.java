package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.logcapturefixture.GlobalAppenderFixture;
import org.danteplanner.logcapturefixture.ScopedCaptureFixture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogCaptureRuleTest {

    @Test
    void appenderAttachedOutsideLogCapture_WhenChecked_FailsNamingTheMethod() {
        JavaClasses global = new ClassFileImporter().importClasses(GlobalAppenderFixture.class);

        assertThatThrownBy(() -> LogCaptureRule.APPENDERS_ATTACH_ONLY_THROUGH_LOG_CAPTURE.check(global))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(GlobalAppenderFixture.class.getName()
                        + ".subject_WhenLogged_IsCapturedGlobally() attaches a logback appender outside LogCapture")
                .hasMessageContaining(GlobalAppenderFixture.class.getName()
                        + ".subjectByReference_WhenLogged_IsCapturedGlobally() attaches a logback appender outside"
                        + " LogCapture");
    }

    @Test
    void logCaptureAndATestUsingIt_WhenChecked_Pass() {
        JavaClasses scoped = new ClassFileImporter().importClasses(LogCapture.class, ScopedCaptureFixture.class);

        assertThatCode(() -> LogCaptureRule.APPENDERS_ATTACH_ONLY_THROUGH_LOG_CAPTURE.check(scoped))
                .doesNotThrowAnyException();
    }
}
