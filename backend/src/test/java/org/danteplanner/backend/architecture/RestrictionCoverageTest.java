package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class RestrictionCoverageTest {

    @ArchTest
    static final ArchRule write_handlers_declare_exactly_one_restriction =
            RestrictionCoverageRule.WRITE_HANDLERS_DECLARE_EXACTLY_ONE_RESTRICTION;

    @ArchTest
    static final ArchRule exemptions_name_a_reason =
            RestrictionCoverageRule.EXEMPTIONS_NAME_A_REASON;

    @ArchTest
    static final ArchRule read_handlers_declare_no_restriction =
            RestrictionCoverageRule.READ_HANDLERS_DECLARE_NO_RESTRICTION;
}
