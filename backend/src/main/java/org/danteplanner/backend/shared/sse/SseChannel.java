package org.danteplanner.backend.shared.sse;

/**
 * Redis pub/sub channels for cross-node SSE fan-out.
 *
 * <p>A subscriber switches over these constants, so a channel added here without a
 * dispatch branch fails to compile rather than dropping its traffic at runtime.</p>
 */
public enum SseChannel {

    USER("sse:user"),
    COMMENT("sse:comment"),
    BROADCAST("sse:broadcast");

    private final String topic;

    SseChannel(String topic) {
        this.topic = topic;
    }

    /**
     * @return the Redis channel name carried on the wire
     */
    public String topic() {
        return topic;
    }

    /**
     * @param topic a Redis channel name as received from a message
     * @return the matching channel, or null when no channel claims the name
     */
    public static SseChannel fromTopic(String topic) {
        for (SseChannel channel : values()) {
            if (channel.topic.equals(topic)) {
                return channel;
            }
        }
        return null;
    }
}
