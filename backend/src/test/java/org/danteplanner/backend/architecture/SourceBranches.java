package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.AnnotatedTypeTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaModifier;

final class SourceBranches {

    private static final int ENUM_CONSTRUCTOR_EXTRAS = 2;

    private static final Pattern LOCAL_CLASS_NUMBER = Pattern.compile("\\$\\d+");

    private SourceBranches() {
    }

    static Map<String, List<Integer>> countsByKey(Path sourceRoot) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> problems = new DiagnosticCollector<>();
        Map<String, List<Integer>> counts = new TreeMap<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(problems, null, null);
                Stream<Path> walked = Files.walk(sourceRoot)) {
            List<Path> sources = walked.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            JavacTask task = (JavacTask) compiler.getTask(null, files, problems, List.of(), null,
                    files.getJavaFileObjectsFromPaths(sources));
            for (CompilationUnitTree unit : task.parse()) {
                new BranchScanner(unit, counts).scan(unit, null);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> errors = problems.getDiagnostics().stream()
                .filter(problem -> problem.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .toList();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("cannot parse " + sourceRoot + ": " + errors);
        }
        return counts;
    }

    static List<String> keysOf(JavaCodeUnit unit) {
        JavaClass owner = unit.getOwner();
        String prefix = LOCAL_CLASS_NUMBER.matcher(owner.getName()).replaceAll("\\$") + "." + unit.getName();
        List<String> types = unit.getRawParameterTypes().stream().map(SourceBranches::simpleName).toList();
        if (!unit.isConstructor()) {
            return List.of(prefix + parameters(types));
        }
        List<String> declared = types.subList(Math.min(leadingExtras(owner), types.size()), types.size());
        int fewest = owner.isLocalClass() ? 0 : declared.size();
        return IntStream.rangeClosed(fewest, declared.size())
                .mapToObj(size -> prefix + parameters(declared.subList(0, size)))
                .toList();
    }

    private static int leadingExtras(JavaClass owner) {
        if (owner.isEnum()) {
            return ENUM_CONSTRUCTOR_EXTRAS;
        }
        boolean hasOuterInstance = owner.isInnerClass() && owner.getEnclosingCodeUnit()
                .map(enclosing -> !enclosing.getModifiers().contains(JavaModifier.STATIC))
                .orElse(true);
        return hasOuterInstance ? 1 : 0;
    }

    private static String parameters(List<String> types) {
        return types.stream().collect(Collectors.joining(", ", "(", ")"));
    }

    private static String simpleName(JavaClass type) {
        String dimensions = "";
        JavaClass element = type;
        while (element.isArray()) {
            element = element.getComponentType();
            dimensions += "[]";
        }
        String name = element.getName();
        return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1) + dimensions;
    }

    private static final class BranchScanner extends TreeScanner<Void, Void> {

        private static final int OUTSIDE_ANY_MEMBER = -1;

        private final Map<String, List<Integer>> counts;
        private final String packagePrefix;
        private final Deque<String> classes = new ArrayDeque<>();
        private final Deque<Map<String, List<? extends Tree>>> typeVariables = new ArrayDeque<>();
        private int branches = OUTSIDE_ANY_MEMBER;

        BranchScanner(CompilationUnitTree unit, Map<String, List<Integer>> counts) {
            this.counts = counts;
            this.packagePrefix = unit.getPackageName() == null ? "" : unit.getPackageName() + ".";
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            if (tree.getSimpleName().isEmpty() && branches != OUTSIDE_ANY_MEMBER) {
                classes.push(classes.peek() + "$");
                super.visitClass(tree, unused);
                classes.pop();
                return null;
            }
            int enclosing = branches;
            branches = OUTSIDE_ANY_MEMBER;
            classes.push(classes.isEmpty()
                    ? packagePrefix + tree.getSimpleName()
                    : classes.peek() + "$" + tree.getSimpleName());
            typeVariables.push(bounds(tree.getTypeParameters()));
            super.visitClass(tree, unused);
            typeVariables.pop();
            classes.pop();
            branches = enclosing;
            return null;
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            if (branches != OUTSIDE_ANY_MEMBER) {
                return super.visitMethod(tree, unused);
            }
            typeVariables.push(bounds(tree.getTypeParameters()));
            String key = classes.peek() + "." + tree.getName() + parameters(tree.getParameters().stream()
                    .map(parameter -> erase(parameter.getType()))
                    .toList());
            typeVariables.pop();
            branches = 0;
            super.visitMethod(tree, unused);
            counts.computeIfAbsent(key, absent -> new ArrayList<>()).add(branches);
            branches = OUTSIDE_ANY_MEMBER;
            return null;
        }

        @Override
        public Void visitIf(IfTree tree, Void unused) {
            branch();
            return super.visitIf(tree, unused);
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree tree, Void unused) {
            branch();
            return super.visitConditionalExpression(tree, unused);
        }

        @Override
        public Void visitBinary(BinaryTree tree, Void unused) {
            if (tree.getKind() == Tree.Kind.CONDITIONAL_AND || tree.getKind() == Tree.Kind.CONDITIONAL_OR) {
                branch();
            }
            return super.visitBinary(tree, unused);
        }

        @Override
        public Void visitCase(CaseTree tree, Void unused) {
            if (tree.getGuard() != null) {
                branch();
            }
            return super.visitCase(tree, unused);
        }

        @Override
        public Void visitSwitch(SwitchTree tree, Void unused) {
            branchOnLiteralLabels(tree.getCases());
            return super.visitSwitch(tree, unused);
        }

        @Override
        public Void visitSwitchExpression(SwitchExpressionTree tree, Void unused) {
            branchOnLiteralLabels(tree.getCases());
            return super.visitSwitchExpression(tree, unused);
        }

        private void branchOnLiteralLabels(List<? extends CaseTree> cases) {
            boolean literal = cases.stream()
                    .flatMap(one -> one.getLabels().stream())
                    .filter(ConstantCaseLabelTree.class::isInstance)
                    .map(label -> ((ConstantCaseLabelTree) label).getConstantExpression())
                    .anyMatch(BranchScanner::isStringOrNumber);
            if (literal) {
                branch();
            }
        }

        private static boolean isStringOrNumber(ExpressionTree label) {
            return switch (label.getKind()) {
                case STRING_LITERAL, CHAR_LITERAL, INT_LITERAL -> true;
                case UNARY_MINUS, UNARY_PLUS -> isStringOrNumber(((UnaryTree) label).getExpression());
                default -> false;
            };
        }

        private void branch() {
            if (branches != OUTSIDE_ANY_MEMBER) {
                branches++;
            }
        }

        private static Map<String, List<? extends Tree>> bounds(List<? extends TypeParameterTree> parameters) {
            Map<String, List<? extends Tree>> bounds = new HashMap<>();
            parameters.forEach(parameter -> bounds.put(parameter.getName().toString(), parameter.getBounds()));
            return bounds;
        }

        private String erase(Tree type) {
            return switch (type) {
                case ArrayTypeTree array -> erase(array.getType()) + "[]";
                case ParameterizedTypeTree parameterized -> erase(parameterized.getType());
                case AnnotatedTypeTree annotated -> erase(annotated.getUnderlyingType());
                case MemberSelectTree qualified -> qualified.getIdentifier().toString();
                case IdentifierTree identifier -> eraseVariable(identifier.getName().toString());
                default -> type.toString();
            };
        }

        private String eraseVariable(String name) {
            return typeVariables.stream()
                    .filter(variables -> variables.containsKey(name))
                    .findFirst()
                    .map(variables -> variables.get(name).isEmpty() ? "Object" : erase(variables.get(name).get(0)))
                    .orElse(name);
        }
    }
}
