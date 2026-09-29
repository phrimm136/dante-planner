package org.danteplanner.ratchetfixture;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

public class RatchetFixtureRules {

    static final ArchRule FAILED_DIRECTLY = classes().should().haveSimpleNameEndingWith("Absent");

    static final ArchRule FAILED_THROUGH_A_HELPER = classes().should().haveSimpleNameEndingWith("Missing");

    static final ArchRule PASSED_ONLY = classes().should().haveSimpleNameStartingWith("Ratchet");

    static final ArchRule NEVER_CHECKED = classes().should().haveSimpleNameEndingWith("Unused");

    static final ArchRule OLD_RULE = classes().should().haveSimpleNameEndingWith("Stale");

    static final ArchRule NEW_RULE = classes().should().haveSimpleNameStartingWith("Ratchet");

    static final ArchRule TYPE_ONLY = classes().should().haveSimpleNameEndingWith("Untyped");

    static final ArchRule ALIAS_OF_FAILED_DIRECTLY = FAILED_DIRECTLY;
}
