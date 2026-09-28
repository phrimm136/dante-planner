package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.danteplanner.backend.architecture.fixture.BoundaryFloorReader;
import org.danteplanner.backend.architecture.fixture.FloorSelectionOutsideModule;
import org.danteplanner.backend.architecture.fixture.RawFloorReader;
import org.danteplanner.backend.planner.floor.FloorSelection;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Raw floor JSON is read only by the floor module's parse boundary (ADR 138), and the classes that
 * still read it directly are frozen by name with a staleness check (ADR 067).
 *
 * <p>A raw read is a floor field name passed as a literal to {@code JsonNode.get/path/has(String)}.
 * ArchUnit sees neither call arguments nor string constants, so the call sites are read from the
 * bytecode: the literal is the {@code ldc} immediately before the call. Reading
 * {@code floorSelections} in a method that hands it to {@code FloorBoundary.parse} is the sanctioned
 * form; any other floor field read outside the module is not.</p>
 */
class FloorBoundaryConfinementTest {

    private static final String MAIN = "org.danteplanner.backend.";
    private static final String FLOOR_MODULE = MAIN + "planner.floor.";

    private static final String FLOOR_SELECTIONS = "floorSelections";
    private static final Set<String> FLOOR_FIELDS = Set.of(FLOOR_SELECTIONS, "themePackId", "giftIds", "difficulty");
    private static final Set<String> KEYED_ACCESSORS = Set.of("get", "path", "has");
    private static final String STRING_ARGUMENT = "(Ljava/lang/String;)";
    private static final String FLOOR_BOUNDARY = "org/danteplanner/backend/planner/floor/FloorBoundary";

    private static final Set<String> FROZEN_RAW_FLOOR_READERS = Set.of(
            MAIN + "planner.validation.PlannerIdMigrations");

    static final ArchRule FLOOR_SELECTIONS_ARE_BUILT_BY_THE_FLOOR_MODULE =
            noClasses()
                    .that().resideOutsideOfPackage("..planner.floor..")
                    .should().callConstructorWhere(target(owner(equivalentTo(FloorSelection.class))))
                    .as("floor selections are built by the floor module's parse boundary");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.danteplanner.backend");

    @Test
    @DisplayName("No class outside the floor module reads floor fields from a JsonNode unless frozen")
    void rawFloorReads_WhenScanned_AreConfinedToTheFloorModule() {
        Set<String> unfrozen = new TreeSet<>(rawFloorReaders(PRODUCTION));
        unfrozen.removeAll(FROZEN_RAW_FLOOR_READERS);

        assertThat(unfrozen)
                .as("these read floor fields from a JsonNode outside planner.floor; parse the floors "
                        + "through FloorBoundary.parse and take the typed FloorSelection instead")
                .isEmpty();
    }

    @Test
    @DisplayName("Every frozen raw floor reader still reads raw floors")
    void frozenRawFloorReaders_WhenScanned_StillReadRawFloors() {
        Set<String> stale = new TreeSet<>(FROZEN_RAW_FLOOR_READERS);
        stale.removeAll(rawFloorReaders(PRODUCTION));

        assertThat(stale)
                .as("these no longer read raw floors, so the entry excuses a read nobody makes; "
                        + "delete it and let the rule cover the class again")
                .isEmpty();
    }

    @Test
    @DisplayName("A class reading a floor field from a JsonNode is named by the rule")
    void rawFloorReader_WhenScanned_IsNamed() {
        JavaClasses fixture = new ClassFileImporter().importClasses(RawFloorReader.class);

        assertThat(rawFloorReaders(fixture)).containsExactly(RawFloorReader.class.getName());
    }

    @Test
    @DisplayName("Handing floorSelections to the parse boundary, or naming it in a message, is not a raw read")
    void boundaryFloorReader_WhenScanned_IsNotNamed() {
        JavaClasses fixture = new ClassFileImporter().importClasses(BoundaryFloorReader.class);

        assertThat(rawFloorReaders(fixture)).isEmpty();
    }

    @Test
    @DisplayName("No production class outside the floor module builds a FloorSelection")
    void floorSelectionConstruction_WhenScanned_StaysInTheFloorModule() {
        assertThatCode(() -> FLOOR_SELECTIONS_ARE_BUILT_BY_THE_FLOOR_MODULE.check(PRODUCTION))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A class building a FloorSelection outside the floor module fails naming the class")
    void floorSelectionOutsideModule_WhenScanned_FailsNamingTheClass() {
        JavaClasses fixture = new ClassFileImporter().importClasses(FloorSelectionOutsideModule.class);

        assertThatThrownBy(() -> FLOOR_SELECTIONS_ARE_BUILT_BY_THE_FLOOR_MODULE.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(FloorSelectionOutsideModule.class.getName());
    }

    private static Set<String> rawFloorReaders(JavaClasses classes) {
        Set<String> readers = new TreeSet<>();
        classes.stream()
                .filter(javaClass -> !javaClass.getName().startsWith(FLOOR_MODULE))
                .filter(FloorBoundaryConfinementTest::readsRawFloors)
                .forEach(javaClass -> readers.add(topLevel(javaClass.getName())));
        return readers;
    }

    private static String topLevel(String className) {
        int nested = className.indexOf('$');
        return nested < 0 ? className : className.substring(0, nested);
    }

    private static boolean readsRawFloors(JavaClass javaClass) {
        String resource = javaClass.getName().replace('.', '/') + ".class";
        try (InputStream bytes = FloorBoundaryConfinementTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (bytes == null) {
                throw new IllegalStateException("no class file for " + javaClass.getName());
            }
            RawFloorScan scan = new RawFloorScan();
            new ClassReader(bytes).accept(scan, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return scan.found;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isJsonNode(String internalName) {
        if (internalName.startsWith("[")) {
            return false;
        }
        try {
            Class<?> owner = Class.forName(internalName.replace('/', '.'), false,
                    FloorBoundaryConfinementTest.class.getClassLoader());
            return JsonNode.class.isAssignableFrom(owner);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("cannot resolve call owner " + internalName, e);
        }
    }

    private static final class RawFloorScan extends ClassVisitor {

        private boolean found;

        RawFloorScan() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            return new MethodScan();
        }

        private final class MethodScan extends MethodVisitor {

            private final Set<String> readFields = new HashSet<>();
            private String pendingLiteral;
            private boolean handsToBoundary;

            MethodScan() {
                super(Opcodes.ASM9);
            }

            @Override
            public void visitLdcInsn(Object value) {
                pendingLiteral = value instanceof String literal && FLOOR_FIELDS.contains(literal) ? literal : null;
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                    boolean isInterface) {
                if (pendingLiteral != null && KEYED_ACCESSORS.contains(name)
                        && descriptor.startsWith(STRING_ARGUMENT) && isJsonNode(owner)) {
                    readFields.add(pendingLiteral);
                }
                if (owner.equals(FLOOR_BOUNDARY) && name.equals("parse")) {
                    handsToBoundary = true;
                }
                pendingLiteral = null;
            }

            @Override
            public void visitInsn(int opcode) {
                pendingLiteral = null;
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                pendingLiteral = null;
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                pendingLiteral = null;
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                pendingLiteral = null;
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                pendingLiteral = null;
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                    Object... bootstrapMethodArguments) {
                pendingLiteral = null;
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                pendingLiteral = null;
            }

            @Override
            public void visitIincInsn(int varIndex, int increment) {
                pendingLiteral = null;
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                pendingLiteral = null;
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                pendingLiteral = null;
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                pendingLiteral = null;
            }

            @Override
            public void visitEnd() {
                boolean unsanctioned = readFields.stream()
                        .anyMatch(field -> !field.equals(FLOOR_SELECTIONS) || !handsToBoundary);
                found |= unsanctioned;
            }
        }
    }
}
