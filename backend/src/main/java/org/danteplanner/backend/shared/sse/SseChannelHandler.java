package org.danteplanner.backend.shared.sse;

public interface SseChannelHandler {

    void deliver(SseEnvelope.CommentEvent event, Object clientPayload);
}
