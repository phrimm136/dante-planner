package org.danteplanner.backend.shared.entity;

public final class EnumLookup {

    private EnumLookup() {
    }

    public static <E extends Enum<E> & ValuedEnum> E fromValue(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getValue().equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + value);
    }

    public static <E extends Enum<E> & ValuedEnum> boolean isValid(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
