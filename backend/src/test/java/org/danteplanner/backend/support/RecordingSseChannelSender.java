package org.danteplanner.backend.support;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SseChannelSender;
import org.danteplanner.backend.shared.sse.SseEnvelope;

public class RecordingSseChannelSender extends SseChannelSender {

    public enum Operation {
        SEND
    }

    public sealed interface Attempt permits Sent, Failed {
    }

    public record Sent(String topic, String json) implements Attempt {
    }

    public record Failed(Operation operation, RuntimeException failure) implements Attempt {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Attempt> attempts = new ArrayList<>();
    private final ScriptedFailures<Operation> failures =
            new ScriptedFailures<>(RecordingSseChannelSender.class.getSimpleName(), Operation.class);

    public RecordingSseChannelSender() {
        super(null);
    }

    public void failNext(Operation operation, RuntimeException failure) {
        failures.failNext(operation, failure);
    }

    @Override
    public void send(String topic, String json) {
        failures.throwIfScripted(Operation.SEND, failure -> attempts.add(new Failed(Operation.SEND, failure)));
        attempts.add(new Sent(topic, json));
    }

    public List<Attempt> attempts() {
        return List.copyOf(attempts);
    }

    public List<Sent> sent() {
        return attempts.stream().filter(Sent.class::isInstance).map(Sent.class::cast).toList();
    }

    public List<SseEnvelope> envelopes(SseChannel channel) {
        return sent().stream()
                .filter(message -> message.topic().equals(channel.topic()))
                .map(message -> parse(message.json()))
                .toList();
    }

    private SseEnvelope parse(String json) {
        try {
            return objectMapper.readValue(json, SseEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
