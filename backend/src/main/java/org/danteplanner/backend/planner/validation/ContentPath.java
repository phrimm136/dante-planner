package org.danteplanner.backend.planner.validation;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record ContentPath(List<Segment> segments) implements Comparable<ContentPath> {

    public sealed interface Segment permits Field, Index, Key {
    }

    public record Field(String name) implements Segment {
    }

    public record Index(int value) implements Segment {
    }

    public record Key(String name) implements Segment {
    }

    private record SegmentPair(Segment left, Segment right) {
    }

    private static final String FIELD_SEPARATOR = ".";
    private static final String OPEN_BRACKET = "[";
    private static final String CLOSE_BRACKET = "]";

    public ContentPath {
        segments = List.copyOf(segments);
    }

    public static ContentPath root() {
        return new ContentPath(List.of());
    }

    public static ContentPath of(String field) {
        return root().field(field);
    }

    public ContentPath field(String name) {
        return then(new Field(name));
    }

    public ContentPath index(int value) {
        return then(new Index(value));
    }

    public ContentPath key(String name) {
        return then(new Key(name));
    }

    public boolean within(ContentPath ancestor) {
        return segments.size() >= ancestor.segments.size()
                && segments.subList(0, ancestor.segments.size()).equals(ancestor.segments);
    }

    public String render() {
        return IntStream.range(0, segments.size())
                .mapToObj(position -> rendered(segments.get(position), position == 0))
                .collect(Collectors.joining());
    }

    @Override
    public int compareTo(ContentPath other) {
        return IntStream.range(0, Math.min(segments.size(), other.segments.size()))
                .map(position -> compare(segments.get(position), other.segments.get(position)))
                .filter(order -> order != 0)
                .findFirst()
                .orElse(Integer.compare(segments.size(), other.segments.size()));
    }

    private ContentPath then(Segment segment) {
        return new ContentPath(Stream.concat(segments.stream(), Stream.of(segment)).toList());
    }

    private static String rendered(Segment segment, boolean leading) {
        return switch (segment) {
            case Field(String name) -> leading ? name : FIELD_SEPARATOR + name;
            case Index(int value) -> OPEN_BRACKET + value + CLOSE_BRACKET;
            case Key(String name) -> OPEN_BRACKET + name + CLOSE_BRACKET;
        };
    }

    private static int compare(Segment left, Segment right) {
        return switch (new SegmentPair(left, right)) {
            case SegmentPair(Field(String leftName), Field(String rightName)) -> leftName.compareTo(rightName);
            case SegmentPair(Index(int leftValue), Index(int rightValue)) -> Integer.compare(leftValue, rightValue);
            case SegmentPair(Key(String leftName), Key(String rightName)) -> leftName.compareTo(rightName);
            default -> Integer.compare(kindRank(left), kindRank(right));
        };
    }

    private static int kindRank(Segment segment) {
        return switch (segment) {
            case Field field -> 0;
            case Index index -> 1;
            case Key key -> 2;
        };
    }
}
