package org.danteplanner.backend.shared.sse;

public enum SseChannel {

    USER("sse:user"),
    COMMENT("sse:comment"),
    BROADCAST("sse:broadcast");

    private final String topic;

    SseChannel(String topic) {
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }

    public static SseChannel fromTopic(String topic) {
        for (SseChannel channel : values()) {
            if (channel.topic.equals(topic)) {
                return channel;
            }
        }
        return null;
    }
}
