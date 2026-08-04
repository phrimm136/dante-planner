package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

    /**
     * Feature locality: a feature's controllers may only depend on service classes the admission
     * policy allows.
     */
    @ArchTest
    static final ArchRule controllers_should_not_use_foreign_feature_services =
            classes()
                    .that().resideInAPackage("..backend.*.controller..")
                    .should(onlyDependOnAdmittedFeatureClassesIn("service"))
                    .as("controllers must not depend on another feature's services");

    /**
     * Feature locality: a feature's services may only depend on repository classes the admission
     * policy allows.
     */
    @ArchTest
    static final ArchRule services_should_not_use_foreign_feature_repositories =
            classes()
                    .that().resideInAPackage("..backend.*.service..")
                    .should(onlyDependOnAdmittedFeatureClassesIn("repository"))
                    .as("services must not depend on another feature's repositories");

    private static ArchCondition<JavaClass> onlyDependOnAdmittedFeatureClassesIn(String targetLayer) {
        return new ArchCondition<>("only depend on admitted feature classes in ." + targetLayer) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String sourceFeature = featureOf(javaClass);
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!layerOf(target).equals(targetLayer)) {
                        continue;
                    }
                    String targetFeature = featureOf(target);
                    boolean admitted = sourceFeature.equals(targetFeature) || targetFeature.equals("shared");
                    if (!admitted) {
                        events.add(SimpleConditionEvent.violated(javaClass, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static String featureOf(JavaClass javaClass) {
        return packageSegment(javaClass, 3);
    }

    private static String layerOf(JavaClass javaClass) {
        return packageSegment(javaClass, 4);
    }

    private static String packageSegment(JavaClass javaClass, int index) {
        String[] segments = javaClass.getPackageName().split("\\.", -1);
        return segments.length > index ? segments[index] : "";
    }
}
