package org.danteplanner.backend.planner.validation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Positions {

    private Positions() {
    }

    private static <T> Map<T, Integer> firstIndexOf(List<T> values) {
        return IntStream.range(0, values.size())
                .boxed()
                .collect(Collectors.toMap(values::get, Function.identity(), (first, later) -> first));
    }

    public static <T> List<Integer> firstIndexPerPosition(List<T> values) {
        Map<T, Integer> firstIndex = firstIndexOf(values);
        return values.stream().map(value -> firstIndexIn(firstIndex, value)).toList();
    }

    private static <T> Integer firstIndexIn(Map<T, Integer> firstIndex, T value) {
        Integer first = firstIndex.get(value);
        if (first == null) {
            throw new IllegalStateException("no position was indexed for " + value);
        }
        return first;
    }
}
