package org.danteplanner.backend.planner.validation;

import java.util.List;
import java.util.Map;
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
}
