package org.danteplanner.backend.planner.validation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Positions {

    private Positions() {
    }

    public static <T> Map<T, Integer> firstIndexOf(List<T> values) {
        return IntStream.range(0, values.size())
                .boxed()
                .collect(Collectors.toMap(values::get, Function.identity(), (first, later) -> first));
    }

    public static <T> Optional<T> firstRepeatOf(List<T> values) {
        Map<T, Integer> firstIndex = firstIndexOf(values);
        return IntStream.range(0, values.size())
                .filter(index -> firstIndex.get(values.get(index)) < index)
                .mapToObj(values::get)
                .findFirst();
    }
}
