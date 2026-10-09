package org.danteplanner.consistency;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record History(List<Operation> operations) {

    public History {
        operations = List.copyOf(operations);
    }

    public static History read(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Operation> operations = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            HistoryLine.parse(lines.get(index), index + 1).ifPresent(operations::add);
        }
        return new History(operations);
    }

    public static History of(List<Operation> operations) {
        return new History(operations);
    }

    <T extends Operation> List<T> all(Class<T> type) {
        return operations.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
