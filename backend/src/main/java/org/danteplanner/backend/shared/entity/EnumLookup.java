package org.danteplanner.backend.shared.entity;

/**
 * Shared resolver for {@link ValuedEnum} types.
 * Collapses the duplicated per-enum {@code fromValue}/{@code isValid} lookup loops into one place.
 */
public final class EnumLookup {

    private EnumLookup() {
    }

    /**
     * Resolve the enum constant whose {@link ValuedEnum#getValue()} equals the given value.
     *
     * @param type  the enum class
     * @param value the wire value to resolve (may be null)
     * @param <E>   the enum type
     * @return the matching constant
     * @throws IllegalArgumentException if no constant matches
     */
    public static <E extends Enum<E> & ValuedEnum> E fromValue(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getValue().equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + value);
    }

    /**
     * Check whether any constant of the enum has {@link ValuedEnum#getValue()} equal to the given value.
     *
     * @param type  the enum class
     * @param value the wire value to check (may be null)
     * @param <E>   the enum type
     * @return true if a constant matches, false otherwise
     */
    public static <E extends Enum<E> & ValuedEnum> boolean isValid(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getValue().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
