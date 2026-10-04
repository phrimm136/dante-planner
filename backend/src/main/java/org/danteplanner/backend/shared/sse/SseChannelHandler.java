package org.danteplanner.backend.shared.sse;

public interface SseChannelHandler {

    void deliver(SseEnvelope envelope, Object clientPayload);
}
