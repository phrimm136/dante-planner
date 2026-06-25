package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforceable layer-boundary baseline for the DDD/DDIA refactor (Step 0, guards B12).
 *
 * <p>Encodes the core Controller -> Service -> Repository direction with focused "no upward
 * dependency" rules rather than a full {@code layeredArchitecture()}: the codebase has legitimate
 * cross-cutting callers (scheduler, listener, facade, event) that invoke services, which a strict
 * layered model would reject. The focused rules below pass cleanly against the current code while
 * still locking in that lower layers never reach up into higher ones.</p>
 */
@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayerBoundaryTest {

    /**
     * Direction: services must not depend on controllers. A service reaching into a controller
     * inverts the Controller -> Service flow.
     */
    @ArchTest
    static final ArchRule services_should_not_depend_on_controllers =
            noClasses()
                    .that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .as("services must not depend on controllers (Controller -> Service direction)");

    /**
     * Direction: repositories must not depend on controllers or services. Data-access classes sit
     * at the bottom of the layering and must stay free of upward dependencies.
     */
    @ArchTest
    static final ArchRule repositories_should_not_depend_on_controllers_or_services =
            noClasses()
                    .that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .orShould().dependOnClassesThat().resideInAPackage("..service..")
                    .as("repositories must not depend on controllers or services");

    /**
     * Constructor injection is mandated project-wide; no field may carry {@code @Autowired}.
     */
    @ArchTest
    static final ArchRule no_field_injection =
            fields()
                    .should().notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .as("constructor injection only: no @Autowired field injection");
}
