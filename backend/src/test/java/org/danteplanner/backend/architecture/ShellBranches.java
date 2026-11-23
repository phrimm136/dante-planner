package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaModifier;

final class ShellBranches {

    static final String MAIN_PACKAGE = "org.danteplanner.backend";

    static final Path MAIN_SOURCES = Path.of("src/main/java");

    static final Path STORE = Path.of("config/shell-branches.txt");

    private static final String REGENERATE = "checks be freeze-branches --reason REASON";

    private static final Pattern LINE = Pattern.compile("(.+\\)) # ([1-9]\\d*)");

    private static final Set<JavaModifier> NOT_ROOTS =
            Set.of(JavaModifier.SYNTHETIC, JavaModifier.BRIDGE, JavaModifier.ABSTRACT);

    private ShellBranches() {
    }

    public static void main(String[] arguments) {
        regenerate(STORE, in(ImportedClasses.production(), MAIN_SOURCES, MAIN_PACKAGE), String.join(" ", arguments));
    }

    static void regenerate(Path store, SortedMap<String, Integer> counted, String reason) {
        List<String> raised = raised(read(store), counted);
        if (!raised.isEmpty() && reason.isBlank()) {
            throw new IllegalStateException("refused: " + store + " only shrinks unless `" + REGENERATE
                    + "` states why these lines are added or raised:\n" + String.join("\n", raised));
        }
        write(store, counted);
    }

    static SortedMap<String, Integer> in(JavaClasses classes, Path sourceRoot, String within) {
        Map<String, List<JavaCodeUnit>> unitsByKey = new HashMap<>();
        Map<JavaCodeUnit, List<JavaCodeUnit>> anonymousUnits = new HashMap<>();
        for (JavaClass type : classes) {
            for (JavaCodeUnit unit : type.getCodeUnits()) {
                if (unit.getModifiers().stream().noneMatch(NOT_ROOTS::contains)) {
                    SourceBranches.keysOf(unit)
                            .forEach(key -> unitsByKey.computeIfAbsent(key, absent -> new ArrayList<>()).add(unit));
                }
            }
            if (type.isAnonymousClass()) {
                type.getEnclosingCodeUnit().ifPresent(enclosing -> anonymousUnits
                        .computeIfAbsent(enclosing, absent -> new ArrayList<>()).addAll(type.getCodeUnits()));
            }
        }
        SortedMap<String, Integer> branches = new TreeMap<>();
        SourceBranches.countsByKey(sourceRoot).forEach((key, counts) -> {
            if (counts.stream().allMatch(count -> count == 0)) {
                return;
            }
            List<JavaCodeUnit> units = unitsByKey.getOrDefault(key, List.of());
            if (counts.size() != 1 || units.size() != 1) {
                throw new IllegalStateException(key + " has branches but is declared " + counts.size()
                        + " time(s) in " + sourceRoot + " and compiled to " + units);
            }
            JavaCodeUnit unit = units.get(0);
            if (withItsAnonymousClasses(unit, anonymousUnits).anyMatch(one -> reachesAnEffectButLogging(one, within))) {
                branches.put(unit.getFullName(), counts.get(0));
            }
        });
        return branches;
    }

    static List<String> raised(Map<String, Integer> stored, Map<String, Integer> counted) {
        return counted.keySet().stream()
                .filter(method -> counted.get(method) > stored.getOrDefault(method, 0))
                .map(method -> change(method, stored, counted))
                .toList();
    }

    static List<String> stale(Map<String, Integer> stored, Map<String, Integer> counted) {
        return stored.keySet().stream()
                .filter(method -> counted.getOrDefault(method, 0) < stored.get(method))
                .map(method -> change(method, stored, counted))
                .toList();
    }

    static SortedMap<String, Integer> read(Path store) {
        SortedMap<String, Integer> stored = new TreeMap<>();
        for (String line : lines(store)) {
            Matcher matcher = LINE.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalStateException(store + " holds '" + line + "', which is not 'method # count'");
            }
            if (stored.put(matcher.group(1), Integer.parseInt(matcher.group(2))) != null) {
                throw new IllegalStateException(store + " holds " + matcher.group(1) + " more than once");
            }
        }
        return stored;
    }

    static void write(Path store, SortedMap<String, Integer> branches) {
        try {
            Files.write(store, branches.entrySet().stream().map(ShellBranches::line).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> lines(Path store) {
        try {
            return Files.readAllLines(store);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String line(Map.Entry<String, Integer> entry) {
        return entry.getKey() + " # " + entry.getValue();
    }

    private static String change(String method, Map<String, Integer> stored, Map<String, Integer> counted) {
        return method + " " + stored.getOrDefault(method, 0) + " -> " + counted.getOrDefault(method, 0);
    }

    private static Stream<JavaCodeUnit> withItsAnonymousClasses(JavaCodeUnit unit,
            Map<JavaCodeUnit, List<JavaCodeUnit>> anonymousUnits) {
        return Stream.concat(Stream.of(unit), anonymousUnits.getOrDefault(unit, List.of()).stream()
                .flatMap(inner -> withItsAnonymousClasses(inner, anonymousUnits)));
    }

    private static boolean reachesAnEffectButLogging(JavaCodeUnit unit, String within) {
        return Effects.reachableFrom(unit, within, callee -> false).keySet().stream()
                .anyMatch(effect -> !effect.equals("log"));
    }
}
