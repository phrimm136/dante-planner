package org.danteplanner.backend.shared.entity;

/**
 * Marker interface for enums that expose a stable wire/string value distinct from their
 * {@link Enum#name()}. Lets {@link EnumLookup} resolve any such enum generically,
 * removing the duplicated {@code fromValue}/{@code isValid} lookup loops.
 */
public interface ValuedEnum {
    String getValue();
}
