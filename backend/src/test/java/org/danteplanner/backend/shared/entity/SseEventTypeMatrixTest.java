package org.danteplanner.backend.shared.entity;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every SSE event type, and what a subscriber does with it.
 *
 * <p>Two properties of an event type are invisible at its declaration and only surface at a client:
 * the wire value it is published under, which a frontend listener matches literally, and the
 * delivery a subscriber routes it by. Neither has a compiler behind it, so both are pinned here.</p>
 *
 * <p>The axis is derived from {@link SseEventType#values()}. A constant added without a row fails
 * rather than joining the enum with an unexamined wire value, which is how a client silently stops
 * receiving an event it was never told the name of.</p>
 */
class SseEventTypeMatrixTest {

    /**
     * The declared wire value and user-channel delivery of every event type. Changing a wire value
     * here is changing a published contract: a listener keyed to the old string goes quiet.
     */
    private static Map<SseEventType, String[]> table() {
        Map<SseEventType, String[]> rows = new LinkedHashMap<>();
        rows.put(SseEventType.COMMENT_ADDED, new String[] {"comment:added", "EMITTERS", "envelope"});
        rows.put(SseEventType.NOTIFY_COMMENT, new String[] {"notify:comment", "EMITTERS", "payload"});
        rows.put(SseEventType.NOTIFY_PUBLISHED, new String[] {"notify:published", "EMITTERS", "payload"});
        rows.put(SseEventType.NOTIFY_RECOMMENDED, new String[] {"notify:recommended", "EMITTERS", "payload"});
        rows.put(SseEventType.SETTINGS_INVALIDATED,
                new String[] {"settings:invalidated", "SETTINGS_CACHE", "envelope"});
        rows.put(SseEventType.ACCOUNT_SUSPENDED,
                new String[] {"account_suspended", "SUSPENSION_NOTICE", "payload"});
        return rows;
    }

    @TestFactory
    Stream<DynamicTest> eventType_WhenDeclared_KeepsItsWireValueAndDelivery() {
        return table().entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey().name() + " -> " + entry.getValue()[0],
                () -> {
                    SseEventType type = entry.getKey();
                    String[] expected = entry.getValue();
                    assertEquals(expected[0], type.getValue(), "wire value is a published contract");
                    assertEquals(expected[1], type.userDelivery().name(),
                            "delivery decides which subscriber branch runs");
                    assertEquals("payload".equals(expected[2]), type.deliversRawPayload(),
                            "a client schema expecting top-level fields breaks on the envelope");
                }));
    }

    @TestFactory
    Stream<DynamicTest> eventType_WhenAdded_HasARowInThisTable() {
        return Arrays.stream(SseEventType.values()).map(type -> DynamicTest.dynamicTest(
                type.name() + " is covered",
                () -> assertTrue(table().containsKey(type),
                        type.name() + " has no row; state its wire value and delivery")));
    }

    @TestFactory
    Stream<DynamicTest> eventType_WhenDeclared_NamesADelivery() {
        return Arrays.stream(SseEventType.values()).map(type -> DynamicTest.dynamicTest(
                type.name() + " names a delivery",
                // The subscriber switches on this exhaustively; a null would fall through to no
                // branch at all and the event would reach nobody.
                () -> assertNotNull(type.userDelivery())));
    }

    @TestFactory
    Stream<DynamicTest> wireValue_WhenTypesAreCompared_IsUniqueAcrossTheEnum() {
        return Arrays.stream(SseEventType.values()).map(type -> DynamicTest.dynamicTest(
                type.getValue() + " is claimed once",
                () -> assertEquals(1,
                        Arrays.stream(SseEventType.values())
                                .filter(other -> other.getValue().equals(type.getValue()))
                                .count(),
                        // Two types sharing a wire value would deliver each other's payloads to a
                        // listener that cannot tell them apart.
                        "wire value " + type.getValue() + " is declared by more than one type")));
    }
}
