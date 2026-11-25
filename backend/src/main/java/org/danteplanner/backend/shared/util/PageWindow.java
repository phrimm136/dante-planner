package org.danteplanner.backend.shared.util;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public sealed interface PageWindow {

    int DEFAULT_PAGE = 0;
    int DEFAULT_SIZE = 20;
    int MAX_SIZE = 100;
    char REPEATED_VALUE_SEPARATOR = ',';

    record InRange(int page, int size) implements PageWindow {
    }

    record OutOfRange(String parameter, Object value) implements PageWindow {
    }

    static PageWindow of(int page, int size) {
        if (page < 0) {
            return new OutOfRange("page", page);
        }
        return size < 1 ? new OutOfRange("size", size) : new InRange(page, Math.min(size, MAX_SIZE));
    }

    static PageWindow of(@Nullable String page, @Nullable String size) {
        return switch (WholeNumber.parse(page, DEFAULT_PAGE)) {
            case WholeNumber.Unreadable unreadable -> new OutOfRange("page", unreadable.supplied());
            case WholeNumber.Read pageNumber -> switch (WholeNumber.parse(size, DEFAULT_SIZE)) {
                case WholeNumber.Unreadable unreadable -> new OutOfRange("size", unreadable.supplied());
                case WholeNumber.Read pageSize -> of(pageNumber.value(), pageSize.value());
            };
        };
    }

    sealed interface WholeNumber {

        record Read(int value) implements WholeNumber {
        }

        record Unreadable(String supplied) implements WholeNumber {
        }

        private static WholeNumber parse(@Nullable String supplied, int absent) {
            if (supplied == null || supplied.isEmpty()) {
                return new Read(absent);
            }
            int separator = supplied.indexOf(REPEATED_VALUE_SEPARATOR);
            String first = separator < 0 ? supplied : supplied.substring(0, separator);
            try {
                return new Read(Integer.parseInt(first.strip()));
            } catch (NumberFormatException e) {
                return new Unreadable(supplied);
            }
        }
    }

    static Pageable pageable(int page, int size) {
        return pageable(of(page, size));
    }

    static Pageable pageable(@Nullable String page, @Nullable String size) {
        return pageable(of(page, size));
    }

    private static Pageable pageable(PageWindow requested) {
        return switch (requested) {
            case InRange window -> PageRequest.of(window.page(), window.size());
            case OutOfRange outOfRange -> {
                TypeMismatchException mismatch = new TypeMismatchException(outOfRange.value(), int.class);
                mismatch.initPropertyName(outOfRange.parameter());
                throw mismatch;
            }
        };
    }
}
