package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassReader;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.MethodVisitor;
import com.tngtech.archunit.thirdparty.org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class Effects {

    private static final String ROOT = "org.danteplanner.backend";

    private static final List<String> PURE_PREFIXES = List.of(
            "java.lang.String", "java.lang.StringBuilder", "java.lang.Character", "java.lang.Integer",
            "java.lang.Long", "java.lang.Boolean", "java.lang.Math", "java.lang.Object",
            "java.lang.Enum", "java.lang.Record", "java.lang.Number", "java.lang.Double",
            "java.lang.CharSequence", "java.lang.Iterable", "java.lang.Comparable", "java.lang.Class",
            "java.lang.Throwable", "java.lang.Exception", "java.lang.RuntimeException",
            "java.lang.IllegalArgumentException", "java.lang.IllegalStateException",
            "java.lang.NullPointerException", "java.lang.UnsupportedOperationException",
            "java.lang.MatchException", "java.lang.runtime.", "java.lang.invoke.",
            "java.util.", "java.time.", "java.math.", "java.nio.charset.", "java.sql.SQLException",
            "java.security.MessageDigest", "org.hibernate.exception.ConstraintViolationException",
            "tools.jackson.", "com.networknt.schema.", "io.jsonwebtoken.",
            "org.springframework.util.Assert", "org.springframework.http.ProblemDetail",
            "org.springframework.web.ErrorResponseException", "org.springframework.web.method.HandlerMethod",
            "[");

    private static final Set<String> PURE_METHODS = Set.of(
            "org.springframework.security.core.Authentication.getPrincipal",
            "org.springframework.security.core.Authentication.getName",
            "org.springframework.security.core.Authentication.getAuthorities",
            "org.springframework.security.core.Authentication.getDetails",
            "org.springframework.security.core.Authentication.getCredentials",
            "org.springframework.security.core.Authentication.isAuthenticated");

    private static final Set<String> EFFECTFUL_JDK = Set.of(
            "java.lang.Object.wait", "java.lang.Object.notify", "java.lang.Object.notifyAll",
            "java.lang.ThreadLocal.set", "java.lang.ThreadLocal.remove", "java.lang.ThreadLocal.get",
            "java.lang.Thread.sleep", "java.lang.System.nanoTime", "java.lang.System.currentTimeMillis");

    private static final List<Class<?>> COLLECTIONS = List.of(Map.class, Collection.class, Iterator.class);

    private static final Pattern MUTATOR =
            Pattern.compile("(put|add|remove|compute|replace|poll|push|pop|offer).*|clear|merge|retainAll|set|sort");

    private static final Pattern IMMUTABLE_COLLECTION =
            Pattern.compile("java\\.util\\.(ImmutableCollections\\$|Collections\\$(Unmodifiable|Empty|Singleton)).*");

    private static final Set<String> UNREADABLE = ConcurrentHashMap.newKeySet();

    private static final Set<Integer> ARRAY_STORES = Set.of(Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE,
            Opcodes.DASTORE, Opcodes.AASTORE, Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE);

    private static final String CONCURRENT_PACKAGE = "java.util.concurrent";

    private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");

    private static final String CONSTRUCTOR = "<init>";

    private static final Set<String> CLOCK_FACTORIES = Arrays.stream(Clock.class.getMethods())
            .filter(method -> Modifier.isStatic(method.getModifiers()))
            .map(Method::getName)
            .collect(Collectors.toUnmodifiableSet());

    private Effects() {
    }

    static Map<String, List<String>> reachableFrom(JavaCodeUnit root) {
        return reachableFrom(root, ROOT, unit -> false);
    }

    static Map<String, List<String>> reachableFrom(JavaCodeUnit root, String within, Predicate<JavaCodeUnit> opaque) {
        Map<String, List<String>> effects = new LinkedHashMap<>();
        Map<String, List<String>> chainTo = new HashMap<>();
        Deque<JavaCodeUnit> pending = new ArrayDeque<>();
        pending.push(root);
        chainTo.put(name(root), List.of(name(root)));

        while (!pending.isEmpty()) {
            JavaCodeUnit unit = pending.pop();
            List<String> chain = chainTo.get(name(unit));
            collectFieldEffects(unit, within, chain, effects);

            for (JavaAccess<?> call : callsFrom(unit)) {
                String owner = call.getTargetOwner().getFullName();
                if (owner.startsWith(within)) {
                    if (isLocalFunction(call)) {
                        continue;
                    }
                    List<JavaCodeUnit> callees = callees(call, within);
                    if (callees.isEmpty() && !OBJECT_METHODS.contains(call.getName())) {
                        effects.putIfAbsent("port " + call.getTargetOwner().getSimpleName() + "." + call.getName(), chain);
                    }
                    for (JavaCodeUnit callee : callees) {
                        if (!opaque.test(callee) && !chainTo.containsKey(name(callee))) {
                            List<String> next = new ArrayList<>(chain);
                            next.add(callee.getOwner().getSimpleName() + "." + callee.getName());
                            chainTo.put(name(callee), next);
                            pending.push(callee);
                        }
                    }
                    continue;
                }
                String target = owner + "." + call.getName();
                if (EFFECTFUL_JDK.contains(target) || call.getName().equals("now") || isEffectfulUtility(call)) {
                    effects.putIfAbsent("jdk " + target, chain);
                } else if (owner.startsWith("org.slf4j.")) {
                    effects.putIfAbsent("log", chain);
                } else if (!isPureOwner(owner) && !PURE_METHODS.contains(target)) {
                    effects.putIfAbsent("external " + target, chain);
                }
            }
        }
        return effects;
    }

    private static boolean isPureOwner(String owner) {
        return PURE_PREFIXES.stream().anyMatch(pure -> pure.endsWith(".") || pure.equals("[")
                ? owner.startsWith(pure)
                : owner.equals(pure));
    }

    private static void collectFieldEffects(JavaCodeUnit unit, String within, List<String> chain,
            Map<String, List<String>> effects) {
        for (JavaFieldAccess access : unit.getFieldAccesses()) {
            JavaClass fieldOwner = access.getTargetOwner();
            if (!fieldOwner.getFullName().startsWith(within)) {
                continue;
            }
            Optional<JavaField> field = access.getTarget().resolveMember();
            if (field.map(resolved -> resolved.getModifiers().contains(JavaModifier.SYNTHETIC)).orElse(false)) {
                continue;
            }
            boolean isStatic = field.map(resolved -> resolved.getModifiers().contains(JavaModifier.STATIC)).orElse(false);
            boolean isSettled = field.map(Effects::isSettled).orElse(false);
            String member = fieldOwner.getSimpleName() + "." + access.getName();
            if (access.getAccessType() == JavaFieldAccess.AccessType.SET && !unit.isConstructor()) {
                effects.putIfAbsent("field-write " + member, chain);
            } else if (isStatic && !isSettled) {
                effects.putIfAbsent("static-field " + member, chain);
            } else if (!isStatic && !isSettled && isSingleton(fieldOwner)) {
                effects.putIfAbsent("bean-field " + member, chain);
            }
        }
    }

    private static List<JavaAccess<?>> callsFrom(JavaCodeUnit unit) {
        List<JavaAccess<?>> calls = new ArrayList<>();
        calls.addAll(unit.getMethodCallsFromSelf());
        calls.addAll(unit.getConstructorCallsFromSelf());
        calls.addAll(unit.getMethodReferencesFromSelf());
        calls.addAll(unit.getConstructorReferencesFromSelf());
        return calls;
    }

    private static List<JavaCodeUnit> callees(JavaAccess<?> call, String within) {
        if (!(call.getTarget() instanceof AccessTarget.CodeUnitAccessTarget target)) {
            return List.of();
        }
        List<JavaCodeUnit> found = new ArrayList<>();
        if (target.resolveMember().isEmpty() && OBJECT_METHODS.contains(target.getName())) {
            for (JavaClass implementation : target.getOwner().getAllSubclasses()) {
                implementation.getCodeUnits().stream()
                        .filter(candidate -> candidate.getName().equals(target.getName()))
                        .filter(candidate -> candidate.getRawParameterTypes().equals(target.getRawParameterTypes()))
                        .forEach(found::add);
            }
        }
        for (JavaCodeUnit member : target.resolveMember().stream().map(JavaCodeUnit.class::cast).toList()) {
            boolean isAbstract = member.getModifiers().contains(JavaModifier.ABSTRACT);
            if (!isAbstract) {
                found.add(member);
            }
            if (isOverridable(member)) {
                for (JavaClass implementation : member.getOwner().getAllSubclasses()) {
                    implementation.getCodeUnits().stream()
                            .filter(candidate -> candidate.getName().equals(member.getName()))
                            .filter(candidate -> candidate.getRawParameterTypes().equals(member.getRawParameterTypes()))
                            .filter(candidate -> !candidate.getModifiers().contains(JavaModifier.ABSTRACT))
                            .forEach(found::add);
                    inheritedBy(implementation, member, within).ifPresent(found::add);
                }
            }
        }
        return found;
    }

    private static Optional<JavaCodeUnit> inheritedBy(JavaClass implementation, JavaCodeUnit member, String within) {
        return implementation.getAllRawSuperclasses().stream()
                .filter(superclass -> superclass.getFullName().startsWith(within))
                .flatMap(superclass -> superclass.getCodeUnits().stream())
                .filter(candidate -> candidate.getName().equals(member.getName()))
                .filter(candidate -> candidate.getRawParameterTypes().equals(member.getRawParameterTypes()))
                .filter(Effects::isInheritable)
                .findFirst();
    }

    private static boolean isInheritable(JavaCodeUnit candidate) {
        return Set.of(JavaModifier.ABSTRACT, JavaModifier.PRIVATE, JavaModifier.STATIC).stream()
                .noneMatch(candidate.getModifiers()::contains);
    }

    private static boolean isOverridable(JavaCodeUnit member) {
        return !member.isConstructor()
                && Set.of(JavaModifier.FINAL, JavaModifier.PRIVATE, JavaModifier.STATIC).stream()
                        .noneMatch(member.getModifiers()::contains);
    }

    private static boolean isSettled(JavaField field) {
        JavaClass type = field.getRawType();
        return field.getModifiers().contains(JavaModifier.FINAL)
                && !type.isAssignableTo(ThreadLocal.class)
                && !type.getPackageName().startsWith(CONCURRENT_PACKAGE)
                && !((type.isArray() || isCollection(type)) && isMutatedAfterConstruction(field)
                        && !holdsAnImmutableCollection(field));
    }

    static Set<String> unreadableFields(String within) {
        return UNREADABLE.stream().filter(field -> field.startsWith(within)).collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean holdsAnImmutableCollection(JavaField field) {
        if (!field.getModifiers().contains(JavaModifier.STATIC) || field.getRawType().isArray()) {
            return false;
        }
        try {
            Field reflected = field.reflect();
            reflected.setAccessible(true);
            Object value = reflected.get(null);
            return value != null && IMMUTABLE_COLLECTION.matcher(value.getClass().getName()).matches();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            UNREADABLE.add(field.getFullName());
            return false;
        }
    }

    private static boolean isCollection(JavaClass type) {
        return COLLECTIONS.stream().anyMatch(type::isAssignableTo);
    }

    private static boolean isMutatedAfterConstruction(JavaField field) {
        return field.getAccessesToSelf().stream()
                .filter(access -> access.getAccessType() == JavaFieldAccess.AccessType.GET)
                .map(JavaFieldAccess::getOrigin)
                .filter(reader -> !isInitializer(reader, field.getOwner()))
                .anyMatch(reader -> field.getRawType().isArray() ? storesIntoAnArray(reader) : mutatesACollection(reader));
    }

    private static boolean isInitializer(JavaCodeUnit unit, JavaClass declaring) {
        return unit.getOwner().equals(declaring)
                && (unit.getName().equals(CONSTRUCTOR) || unit.getName().equals("<clinit>"));
    }

    private static boolean mutatesACollection(JavaCodeUnit unit) {
        return callsFrom(unit).stream().anyMatch(call -> isCollection(call.getTargetOwner())
                && MUTATOR.matcher(call.getName()).matches());
    }

    private static boolean storesIntoAnArray(JavaCodeUnit unit) {
        boolean[] stores = {false};
        String resource = "/" + unit.getOwner().getName().replace('.', '/') + ".class";
        try (InputStream bytes = Effects.class.getResourceAsStream(resource)) {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                        String[] exceptions) {
                    if (!name.equals(unit.getName()) || !descriptor.equals(unit.getDescriptor())) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitInsn(int opcode) {
                            stores[0] |= ARRAY_STORES.contains(opcode);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return stores[0];
    }

    private static boolean isLocalFunction(JavaAccess<?> call) {
        JavaClass target = call.getTargetOwner();
        return target.isInterface()
                && target.isAnnotatedWith(FunctionalInterface.class)
                && !target.getModifiers().contains(JavaModifier.PUBLIC)
                && target.getEnclosingClass().isPresent()
                && topLevel(target).equals(topLevel(call.getOriginOwner()));
    }

    private static JavaClass topLevel(JavaClass type) {
        JavaClass current = type;
        while (current.getEnclosingClass().isPresent()) {
            current = current.getEnclosingClass().get();
        }
        return current;
    }

    private static boolean isSingleton(JavaClass owner) {
        return owner.isMetaAnnotatedWith("org.springframework.stereotype.Component")
                || owner.getMethodsWithReturnTypeOfSelf().stream()
                        .anyMatch(factory -> factory.isAnnotatedWith("org.springframework.context.annotation.Bean"));
    }

    private static boolean isEffectfulUtility(JavaAccess<?> call) {
        String owner = call.getTargetOwner().getFullName();
        String name = call.getName();
        boolean noArguments = call.getTarget() instanceof AccessTarget.CodeUnitAccessTarget target
                && target.getRawParameterTypes().isEmpty();
        return owner.startsWith(CONCURRENT_PACKAGE)
                || owner.equals("java.util.Random")
                || owner.startsWith("java.util.Timer")
                || (owner.equals("java.util.UUID") && name.equals("randomUUID"))
                || (owner.equals("java.util.Date") && name.equals(CONSTRUCTOR) && noArguments)
                || (owner.equals("java.time.Clock") && !CLOCK_FACTORIES.contains(name));
    }

    static String name(JavaCodeUnit unit) {
        return unit.getOwner().getFullName() + "." + unit.getName()
                + unit.getRawParameterTypes().stream().map(JavaClass::getSimpleName).toList();
    }
}
