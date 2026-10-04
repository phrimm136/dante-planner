package org.danteplanner.backend.architecture;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

import org.danteplanner.backend.shared.TypeLabel;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "org.danteplanner.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class FeatureBoundaryTest {

    private static final String PROJECT = "org.danteplanner.";
    private static final String MODULE_PARENT = "backend";
    private static final String SHARED = "shared";
    private static final String API = "api";

    private static final List<String> ROLE_WORDS = List.of("Provider", "Participant", "Target", "Handler");

    private static final Map<String, String> FROZEN = Map.of(
            "moderation.controller.ModerationController", "user",
            "moderation.service.ModerationPolicy", "user",
            "moderation.service.UserModerationService", "user");

    private static final DescribedPredicate<JavaClass> IN_SHARED = DescribedPredicate.describe(
            "reside in shared", javaClass -> SHARED.equals(moduleOf(javaClass)));

    private static final DescribedPredicate<JavaClass> IN_A_MODULE = DescribedPredicate.describe(
            "reside in a module", javaClass -> isModule(moduleOf(javaClass)));

    private static final DescribedPredicate<JavaClass> IN_AN_API = DescribedPredicate.describe(
            "reside in a module's api package", FeatureBoundaryTest::inApi);

    private static final DescribedPredicate<JavaClass> IMPLEMENTED_OUTSIDE_ITS_MODULE = DescribedPredicate.describe(
            "are interfaces declared in a module's api or in shared and implemented outside their module",
            FeatureBoundaryTest::isImplementedOutsideItsModule);

    private static final DescribedPredicate<JavaClass> IMPLEMENT_A_TYPE_LABEL = DescribedPredicate.describe(
            "implement a @TypeLabel interface",
            javaClass -> javaClass.getAllRawInterfaces().stream()
                    .anyMatch(implemented -> implemented.isAnnotatedWith(TypeLabel.class)));

    private static final SliceAssignment BY_MODULE = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String module = moduleOf(javaClass);
            return isModule(module) ? SliceIdentifier.of(module) : SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "modules";
        }
    };

    @ArchTest
    static final ArchRule modules_meet_only_through_published_apis =
            classes()
                    .should(dependOnOtherModulesOnlyThroughTheirApi())
                    .as("a class depends on another module only through that module's api package");

    @ArchTest
    static final ArchRule shared_depends_on_no_module =
            noClasses()
                    .that(IN_SHARED)
                    .should().dependOnClassesThat(IN_A_MODULE)
                    .as("shared is platform: it depends on no module");

    @ArchTest
    static final ArchRule modules_are_free_of_cycles =
            slices()
                    .assignedFrom(BY_MODULE)
                    .should().beFreeOfCycles()
                    .as("the module dependency graph has no cycles");

    @ArchTest
    static final ArchRule api_depends_on_no_internal_of_its_module =
            classes()
                    .that(IN_AN_API)
                    .should(dependOnNoNonApiClassOfTheirOwnModule())
                    .as("an api package depends on no non-api class of its own module");

    @ArchTest
    static final ArchRule interfaces_implemented_across_modules_name_their_role =
            classes()
                    .that(IMPLEMENTED_OUTSIDE_ITS_MODULE)
                    .should(endInARoleWordOrCarryATypeLabel())
                    .as("an interface implemented outside its module ends in Provider, Participant, Target "
                            + "or Handler, or carries @TypeLabel");

    @ArchTest
    static final ArchRule type_labels_are_implemented_only_by_data_types =
            classes()
                    .that(IMPLEMENT_A_TYPE_LABEL)
                    .should(beADataType())
                    .as("only enums, records and interfaces implement a @TypeLabel interface");

    @ArchTest
    static void frozenInternalEdges_WhenScanned_AreStillLive(JavaClasses classes) {
        Set<String> stale = FROZEN.entrySet().stream()
                .filter(entry -> !reachesInternalsOf(classes, entry.getKey(), entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(stale)
                .as("these no longer reach the frozen module's internals, so the entry now excuses an "
                        + "edge nobody has; delete it and let the rule cover the class again")
                .isEmpty();
    }

    private static ArchCondition<JavaClass> dependOnOtherModulesOnlyThroughTheirApi() {
        return new ArchCondition<>("depend on another module only through its api package") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getDirectDependenciesFromSelf().stream()
                        .filter(FeatureBoundaryTest::reachesAnotherModulesInternals)
                        .filter(dependency -> !isFrozen(dependency))
                        .forEach(dependency -> events.add(
                                SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    private static ArchCondition<JavaClass> dependOnNoNonApiClassOfTheirOwnModule() {
        return new ArchCondition<>("depend on no non-api class of their own module") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String module = moduleOf(javaClass);
                javaClass.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> module.equals(moduleOf(dependency.getTargetClass())))
                        .filter(dependency -> !inApi(dependency.getTargetClass()))
                        .forEach(dependency -> events.add(
                                SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    private static ArchCondition<JavaClass> endInARoleWordOrCarryATypeLabel() {
        return new ArchCondition<>("end in a role word or carry @TypeLabel") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean named = ROLE_WORDS.stream().anyMatch(javaClass.getSimpleName()::endsWith);
                boolean labeled = javaClass.isAnnotatedWith(TypeLabel.class);
                events.add(new SimpleConditionEvent(javaClass, named || labeled,
                        javaClass.getFullName() + " is implemented outside its module by "
                                + outsideImplementers(javaClass) + " and neither ends in a role word nor carries "
                                + "@TypeLabel"));
            }
        };
    }

    private static ArchCondition<JavaClass> beADataType() {
        return new ArchCondition<>("be an enum, a record or an interface") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean dataType = javaClass.isInterface()
                        || extendsRaw(javaClass, Enum.class)
                        || extendsRaw(javaClass, Record.class);
                events.add(new SimpleConditionEvent(javaClass, dataType,
                        javaClass.getFullName() + " implements a @TypeLabel interface but is not an enum, "
                                + "a record or an interface"));
            }
        };
    }

    static Set<String> modulesOf(JavaClasses classes) {
        return classes.stream()
                .map(FeatureBoundaryTest::moduleOf)
                .filter(FeatureBoundaryTest::isModule)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean reachesInternalsOf(JavaClasses classes, String origin, String module) {
        return classes.stream()
                .filter(javaClass -> moduleRelativeName(javaClass).equals(origin))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(FeatureBoundaryTest::reachesAnotherModulesInternals)
                .anyMatch(dependency -> module.equals(moduleOf(dependency.getTargetClass())));
    }

    private static boolean isFrozen(Dependency dependency) {
        String frozenModule = FROZEN.get(moduleRelativeName(dependency.getOriginClass()));
        return frozenModule != null && frozenModule.equals(moduleOf(dependency.getTargetClass()));
    }

    private static boolean reachesAnotherModulesInternals(Dependency dependency) {
        JavaClass target = dependency.getTargetClass();
        String targetModule = moduleOf(target);
        return isModule(targetModule)
                && !targetModule.equals(moduleOf(dependency.getOriginClass()))
                && !inApi(target);
    }

    private static boolean isImplementedOutsideItsModule(JavaClass javaClass) {
        if (!javaClass.isInterface() || javaClass.isAnnotation()) {
            return false;
        }
        if (!inApi(javaClass) && !SHARED.equals(moduleOf(javaClass))) {
            return false;
        }
        return !outsideImplementers(javaClass).isEmpty();
    }

    private static Set<String> outsideImplementers(JavaClass declared) {
        String module = moduleOf(declared);
        return declared.getAllSubclasses().stream()
                .filter(implementer -> !module.equals(moduleOf(implementer)))
                .map(JavaClass::getFullName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean extendsRaw(JavaClass javaClass, Class<?> superclass) {
        return javaClass.getAllRawSuperclasses().stream()
                .anyMatch(ancestor -> ancestor.getFullName().equals(superclass.getName()));
    }

    private static boolean inApi(JavaClass javaClass) {
        String module = moduleOf(javaClass);
        if (!isModule(module)) {
            return false;
        }
        String packageName = javaClass.getPackageName() + ".";
        return packageName.contains("." + MODULE_PARENT + "." + module + "." + API + ".");
    }

    private static boolean isModule(String module) {
        return !module.isEmpty() && !SHARED.equals(module);
    }

    private static String moduleOf(JavaClass javaClass) {
        String relative = moduleRelativeName(javaClass);
        int end = relative.indexOf('.');
        return end < 0 ? "" : relative.substring(0, end);
    }

    private static String moduleRelativeName(JavaClass javaClass) {
        String fullName = topLevel(javaClass);
        if (!fullName.startsWith(PROJECT)) {
            return "";
        }
        String marker = "." + MODULE_PARENT + ".";
        int parent = fullName.indexOf(marker);
        return parent < 0 ? "" : fullName.substring(parent + marker.length());
    }

    /**
     * An entry names a source file, so a nested or synthesized class — a Lombok builder, an enum
     * switch map — is frozen with the class it was generated from rather than by a name the
     * compiler chose.
     */
    private static String topLevel(JavaClass javaClass) {
        String fullName = javaClass.getFullName();
        int nested = fullName.indexOf('$');
        return nested < 0 ? fullName : fullName.substring(0, nested);
    }
}
