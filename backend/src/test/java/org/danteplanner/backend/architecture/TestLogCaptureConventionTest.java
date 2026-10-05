package org.danteplanner.backend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "org.danteplanner.backend")
class TestLogCaptureConventionTest {

    @ArchTest
    static final ArchRule appenders_attach_only_through_log_capture =
            LogCaptureRule.APPENDERS_ATTACH_ONLY_THROUGH_LOG_CAPTURE;
}
