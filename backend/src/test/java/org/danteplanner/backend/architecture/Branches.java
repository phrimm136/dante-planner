package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassReader;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Handle;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Label;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.MethodVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Opcodes;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Type;

final class Branches {

    private Branches() {
    }

    static Map<String, Integer> forwardConditionalJumps(JavaClass clazz) {
        Map<String, Integer> counts = new TreeMap<>();
        new ClassReader(bytesOf(clazz)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                if ((access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) {
                    return null;
                }
                String key = name + Arrays.stream(Type.getArgumentTypes(descriptor))
                        .map(Branches::typeName)
                        .collect(Collectors.joining(",", "(", ")"));
                return new JumpCounter(count -> counts.put(key, count));
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return counts;
    }

    private static final class JumpCounter extends MethodVisitor {

        private static final String SWITCH_BOOTSTRAPS = "java/lang/runtime/SwitchBootstraps";

        private final IntConsumer onEnd;
        private final Map<Label, Integer> positions = new HashMap<>();
        private final Map<Label, Integer> loopStartByExit = new HashMap<>();
        private final List<Map.Entry<Integer, Label>> forwardConditionals = new ArrayList<>();
        private final Set<Integer> patternSwitchRestarts = new HashSet<>();
        private final Map<Integer, Integer> lastBackEdgeByLoopStart = new HashMap<>();
        private int position;
        private int backwardGotoTarget = -1;

        JumpCounter(IntConsumer onEnd) {
            super(Opcodes.ASM9);
            this.onEnd = onEnd;
        }

        @Override
        public void visitLabel(Label label) {
            positions.put(label, position);
            if (backwardGotoTarget >= 0) {
                loopStartByExit.put(label, backwardGotoTarget);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label target) {
            Integer targetPosition = positions.get(target);
            if (isConditional(opcode) && targetPosition == null) {
                forwardConditionals.add(Map.entry(position, target));
            }
            instruction();
            if (opcode == Opcodes.GOTO && targetPosition != null && !patternSwitchRestarts.contains(targetPosition)) {
                backwardGotoTarget = targetPosition;
                lastBackEdgeByLoopStart.merge(targetPosition, position - 1, Math::max);
            }
        }

        @Override
        public void visitEnd() {
            onEnd.accept((int) forwardConditionals.stream()
                    .filter(jump -> !isLoopExit(jump.getKey(), jump.getValue()))
                    .count());
        }

        private boolean isLoopExit(int jumpPosition, Label target) {
            Integer loopStart = loopStartByExit.get(target);
            return loopStart != null && loopStart <= jumpPosition
                    && positions.get(target) == lastBackEdgeByLoopStart.get(loopStart) + 1;
        }

        private void instruction() {
            position++;
            backwardGotoTarget = -1;
        }

        @Override
        public void visitInsn(int opcode) {
            instruction();
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            instruction();
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            instruction();
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            instruction();
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            instruction();
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            instruction();
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments) {
            if (bootstrapMethodHandle.getOwner().equals(SWITCH_BOOTSTRAPS)) {
                patternSwitchRestarts.add(position - 2);
            }
            instruction();
        }

        @Override
        public void visitLdcInsn(Object value) {
            instruction();
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            instruction();
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            instruction();
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            instruction();
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            instruction();
        }
    }

    static String key(JavaMethod method) {
        return method.getName() + method.getRawParameterTypes().stream()
                .map(JavaClass::getName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    private static boolean isConditional(int opcode) {
        return (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE)
                || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL;
    }

    private static String typeName(Type type) {
        return type.getSort() == Type.ARRAY ? type.getDescriptor().replace('/', '.') : type.getClassName();
    }

    private static byte[] bytesOf(JavaClass clazz) {
        String resource = clazz.getName().replace('.', '/') + ".class";
        try (InputStream in = Branches.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no class file on the test classpath for " + clazz.getName());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
