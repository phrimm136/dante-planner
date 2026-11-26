package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassReader;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Handle;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Label;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.MethodVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Opcodes;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Type;

import org.danteplanner.backend.shared.security.SpringContextAdapter;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionPurityTest {

    private static final String ROOT = "org.danteplanner.backend";

    private static final String COMMAND_OUTCOME = ROOT + ".shared.failure.CommandOutcome";

    private static final String FIXTURE_PACKAGE = "org.danteplanner.fixture.decisionpurity";

    private static final String PROJECT = "org.danteplanner";

    private static final String OUTCOME_SUFFIX = "Outcome";

    private static final Set<String> CONTEXT_READS = Set.of("viewer", "requestValue", "transactionScope");

    private static final JavaClasses MAIN_CLASSES = ImportedClasses.production();

    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURE_PACKAGE);

    @Test
    void decision_WhenItReachesAnEffect_IsNamed() {
        assertThat(offendersIn(MAIN_CLASSES, ROOT))
                .as("a method returning a sealed type or an enum is a decision and reaches no effect; "
                        + "return the decision from a pure method and let the caller perform the effect, "
                        + "or make the result a command outcome (implements CommandOutcome, named *Outcome)")
                .isEmpty();
    }

    @Test
    void outcome_WhenNameAndMarkerDisagree_IsNamed() {
        assertThat(unpairedIn(MAIN_CLASSES, ROOT))
                .as("a type declaring CommandOutcome ends in Outcome, and a sealed type or enum ending in "
                        + "Outcome implements CommandOutcome")
                .isEmpty();
    }

    @Test
    void waitingDecision_WhenTheRuleRunsOverTheFixture_IsNamedWithTheWait() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE).get("Decider.decide"))
                .contains("jdk java.util.concurrent.CountDownLatch.await");
    }

    @Test
    void monitorWait_WhenTheRuleRunsOverTheFixture_IsNamed() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE))
                .containsEntry("MonitorWaiter.decide", Set.of("jdk java.lang.Object.wait"));
    }

    @Test
    void overriddenMethod_WhenTheRuleRunsOverTheFixture_IsWalkedIntoTheOverride() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE))
                .containsEntry("BaseCaller.decide", Set.of("port Port.isOpen"));
    }

    @Test
    void inheritedImplementation_WhenTheRuleRunsOverTheFixture_IsWalked() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE))
                .containsEntry("AskerCaller.decide", Set.of("port Port.isOpen"));
    }

    @Test
    void portReadingDecision_WhenTheRuleRunsOverTheFixture_IsNamedWithThePort() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE))
                .containsEntry("PortReader.decide", Set.of("port Port.isOpen"));
    }

    @Test
    void resultChain_WhenTheRuleRunsOverTheFixture_IsNamedWithTheEffectInItsLambda() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, PROJECT);

        assertThat(offenders).containsEntry("ChainedPortReader.chain", Set.of("port Port.isOpen"));
        assertThat(offenders).doesNotContainKey("ChainedPortReader.pureChain");
    }

    @Test
    void threadLocalAccessor_WhenTheRuleRunsOverTheFixture_IsAllowedButABranchingReadIsNamed() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE);

        assertThat(offenders).doesNotContainKeys("WindowHolder.window", "WindowHolder.fromWindow");
        assertThat(offenders).containsEntry("WindowHolder.windowOrClosed",
                Set.of("jdk java.lang.ThreadLocal.get", "static-field WindowHolder.HOLDER"));
    }

    @Test
    void command_WhenTheRuleRunsOverTheFixture_MayWait() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE))
                .containsOnlyKeys("Decider.decide", "PortReader.decide", "WindowHolder.windowOrClosed",
                        "Ledger.counted", "PublicPortReader.decide", "ClockReader.decide", "FutureReader.decide",
                        "Minter.decide", "Cache.decide", "Tally.decide", "Seen.decide", "Slots.decide",
                        "Pool.decide", "Broken.decide", "MonitorWaiter.decide", "BaseCaller.decide",
                        "AskerCaller.decide", "Unsigner.decide", "Saver.decide");
    }

    @Test
    void branchFreeWrite_WhenTheRuleRunsOverTheFixture_IsNamed() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE)).containsEntry("Saver.decide", Set.of("port Store.save"));
    }

    @Test
    void authentication_WhenTheRuleRunsOverTheFixture_IsPureToReadAndAnEffectToChange() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE);

        assertThat(offenders).doesNotContainKey("Signer.decide");
        assertThat(offenders).containsEntry("Unsigner.decide",
                Set.of("external org.springframework.security.core.Authentication.setAuthenticated"));
    }

    @Test
    void clockWaitOrRandom_WhenTheRuleRunsOverTheFixture_IsNamedWhileValuesStayPure() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE);

        assertThat(offenders).containsEntry("ClockReader.decide", Set.of("jdk java.time.Clock.instant"));
        assertThat(offenders).containsEntry("FutureReader.decide", Set.of("jdk java.util.concurrent.Future.get"));
        assertThat(offenders).containsEntry("Minter.decide", Set.of("jdk java.util.UUID.randomUUID"));
        assertThat(offenders).doesNotContainKey("PureValues.decide");
    }

    @Test
    void sharedState_WhenTheRuleRunsOverTheFixture_IsNamedOnlyForASingletonOrAStatic() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE);

        assertThat(offenders).containsEntry("Cache.decide",
                Set.of("bean-field Cache.entries", "jdk java.util.concurrent.ConcurrentHashMap.isEmpty"));
        assertThat(offenders).containsEntry("Tally.decide", Set.of("static-field Tally.count"));
        assertThat(offenders).doesNotContainKey("Row.getStatus");
    }

    @Test
    void beanField_WhenTheRuleRunsOverTheFixture_NamesOnlyAMutableHolder() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE);

        assertThat(offenders).doesNotContainKey("Ledger.priced");
        assertThat(offenders).containsEntry("Ledger.counted",
                Set.of("bean-field Ledger.counts", "jdk java.util.concurrent.ConcurrentHashMap.isEmpty"));
    }

    @Test
    void finalStaticHolder_WhenTheRuleRunsOverTheFixture_IsNamedOnlyWhenSomethingMutatesIt() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE);

        assertThat(offenders).doesNotContainKey("Ranks.decide");
        assertThat(offenders).containsEntry("Seen.decide", Set.of("static-field Seen.BY_NAME"));
        assertThat(offenders).containsEntry("Slots.decide", Set.of("static-field Slots.USED"));
    }

    @Test
    void staticCollection_WhenTheRuleRunsOverTheFixture_IsJudgedByItsValue() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE);

        assertThat(offenders).doesNotContainKey("Vocabulary.decide");
        assertThat(offenders).containsEntry("Pool.decide", Set.of("static-field Pool.NAMES"));
    }

    @Test
    void staticCollection_WhenItsValueCannotBeRead_FallsBackToItsUseAndIsReported() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE))
                .containsEntry("Broken.decide", Set.of("static-field Broken.NAMES"));
        assertThat(Effects.unreadableFields(FIXTURE_PACKAGE))
                .containsExactly(FIXTURE_PACKAGE + ".DecisionPurityFixtures$Broken.NAMES");
    }

    @Test
    void mainStaticCollection_WhenItsValueCannotBeRead_IsNamed() {
        offendersIn(MAIN_CLASSES, ROOT);

        assertThat(Effects.unreadableFields(ROOT)).isEmpty();
    }

    @Test
    void equalsOnASealedInterface_WhenTheRuleRunsOverTheFixture_IsWalkedOntoItsRecords() {
        assertThat(offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE)).doesNotContainKey("Comparer.same");
    }

    @Test
    void functionalInterface_WhenTheRuleRunsOverTheFixture_IsAPortOnlyWhenPublic() {
        Map<String, Set<String>> offenders = offendersIn(FIXTURE_CLASSES, FIXTURE_PACKAGE);

        assertThat(offenders).doesNotContainKey("RuleRunner.decide");
        assertThat(offenders).containsEntry("PublicPortReader.decide", Set.of("port PublicReads.read"));
    }

    @Test
    void unpairedOutcome_WhenTheRuleRunsOverTheFixture_IsNamedEitherWay() {
        assertThat(unpairedIn(FIXTURE_CLASSES, FIXTURE_PACKAGE))
                .containsExactly("BarOutcome", "Baz");
    }

    private static Map<String, Set<String>> offendersIn(JavaClasses classes, String within) {
        Map<String, Set<String>> offenders = new TreeMap<>();
        for (JavaClass clazz : classes) {
            for (JavaMethod method : clazz.getMethods()) {
                if (!isDecision(method, within) || isAccessor(method) || isContextRead(method)) {
                    continue;
                }
                Map<String, List<String>> effects = Effects.reachableFrom(method, within,
                        unit -> isAccessor(unit) || isContextRead(unit));
                if (!effects.isEmpty()) {
                    offenders.computeIfAbsent(clazz.getSimpleName() + "." + method.getName(), key -> new TreeSet<>())
                            .addAll(effects.keySet());
                }
            }
        }
        return offenders;
    }

    private static boolean isDecision(JavaMethod method, String within) {
        Set<JavaModifier> modifiers = method.getModifiers();
        if (modifiers.contains(JavaModifier.ABSTRACT) || modifiers.contains(JavaModifier.SYNTHETIC)
                || modifiers.contains(JavaModifier.BRIDGE)) {
            return false;
        }
        JavaClass returned = method.getRawReturnType();
        return returned.getPackageName().startsWith(within)
                && (returned.isEnum() || returned.reflect().isSealed())
                && !isCommandOutcome(returned);
    }

    private static boolean isCommandOutcome(JavaClass type) {
        return type.getAllRawInterfaces().stream().anyMatch(inherited -> COMMAND_OUTCOME.equals(inherited.getFullName()));
    }

    private static Set<String> unpairedIn(JavaClasses classes, String within) {
        Set<String> unpaired = new TreeSet<>();
        for (JavaClass clazz : classes) {
            if (!clazz.getPackageName().startsWith(within)) {
                continue;
            }
            boolean declaresMarker = clazz.getRawInterfaces().stream()
                    .anyMatch(direct -> COMMAND_OUTCOME.equals(direct.getFullName()));
            boolean named = clazz.getSimpleName().endsWith(OUTCOME_SUFFIX);
            boolean decisionShaped = clazz.isEnum() || clazz.reflect().isSealed();
            if ((declaresMarker && !named) || (named && decisionShaped && !isCommandOutcome(clazz))) {
                unpaired.add(clazz.getSimpleName());
            }
        }
        return unpaired;
    }

    private static boolean isContextRead(JavaCodeUnit unit) {
        return unit.getOwner().isEquivalentTo(SpringContextAdapter.class) && CONTEXT_READS.contains(unit.getName());
    }

    private static boolean isAccessor(JavaCodeUnit unit) {
        if (!(unit instanceof JavaMethod method) || !method.getRawParameterTypes().isEmpty()) {
            return false;
        }
        BytecodeShape shape = shapeOf(method);
        return !shape.branches()
                && shape.calls().equals(List.of("java/lang/ThreadLocal.get"))
                && shape.staticReads().size() == 1
                && isStaticFinal(method.getOwner(), shape.staticReads().getFirst());
    }

    private static boolean isStaticFinal(JavaClass owner, String fieldName) {
        return owner.tryGetField(fieldName)
                .map(JavaField::getModifiers)
                .map(modifiers -> modifiers.contains(JavaModifier.STATIC) && modifiers.contains(JavaModifier.FINAL))
                .orElse(false);
    }

    private record BytecodeShape(List<String> calls, List<String> staticReads, boolean branches) {
    }

    private static BytecodeShape shapeOf(JavaMethod method) {
        String descriptor = Type.getMethodDescriptor(method.reflect());
        List<String> calls = new ArrayList<>();
        List<String> staticReads = new ArrayList<>();
        boolean[] branches = {false};
        String resource = "/" + method.getOwner().getName().replace('.', '/') + ".class";
        try (InputStream bytes = DecisionPurityTest.class.getResourceAsStream(resource)) {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                        String[] exceptions) {
                    if (!name.equals(method.getName()) || !desc.equals(descriptor)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String callee, String calleeDesc,
                                boolean isInterface) {
                            calls.add(owner + "." + callee);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String callee, String calleeDesc, Handle bootstrap,
                                Object... arguments) {
                            calls.add("indy." + callee);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String field, String fieldDesc) {
                            if (opcode == Opcodes.GETSTATIC) {
                                staticReads.add(field);
                            } else {
                                calls.add("field." + field);
                            }
                        }

                        @Override
                        public void visitJumpInsn(int opcode, Label label) {
                            branches[0] = true;
                        }

                        @Override
                        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                            branches[0] = true;
                        }

                        @Override
                        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                            branches[0] = true;
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BytecodeShape(List.copyOf(calls), List.copyOf(staticReads), branches[0]);
    }
}
