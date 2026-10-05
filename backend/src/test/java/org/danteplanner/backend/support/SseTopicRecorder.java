package org.danteplanner.backend.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SseEnvelope;

public final class SseTopicRecorder implements AutoCloseable {

    private static final Duration FENCE_TIMEOUT = Duration.ofSeconds(10);

    private final Envelopes envelopes = new Envelopes();
    private final Map<String, CountDownLatch> fences = new ConcurrentHashMap<>();
    private final String fenceTopic = "sse:recorder-fence:" + UUID.randomUUID();
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate fencePublisher;
    private final RedisMessageListenerContainer container;

    private SseTopicRecorder(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.fencePublisher = new StringRedisTemplate(connectionFactory);
        this.container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(new SyncTaskExecutor());
        container.addMessageListener(this::onMessage, List.of(
                new ChannelTopic(SseChannel.USER.topic()),
                new ChannelTopic(SseChannel.COMMENT.topic()),
                new ChannelTopic(SseChannel.BROADCAST.topic()),
                new ChannelTopic(fenceTopic)));
        container.afterPropertiesSet();
        try {
            container.start();
        } catch (RuntimeException notStarted) {
            try {
                container.destroy();
            } catch (Exception destroyFailure) {
                notStarted.addSuppressed(destroyFailure);
            }
            throw notStarted;
        }
    }

    public static SseTopicRecorder open(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        SseTopicRecorder recorder = new SseTopicRecorder(connectionFactory, objectMapper);
        try {
            recorder.fence();
        } catch (RuntimeException | AssertionError notReady) {
            try {
                recorder.close();
            } catch (RuntimeException closeFailure) {
                notReady.addSuppressed(closeFailure);
            }
            throw notReady;
        }
        return recorder;
    }

    public List<SseEnvelope> userEvents(Long userId) {
        return envelopes.userEvents(userId);
    }

    public List<SseEnvelope> userEvents(Long userId, SseEventType type) {
        return envelopes.userEvents(userId, type);
    }

    public List<SseEnvelope> commentEvents(UUID plannerId) {
        return envelopes.commentEvents(plannerId);
    }

    public List<SseEnvelope> commentEvents(UUID plannerId, SseEventType type) {
        return envelopes.commentEvents(plannerId, type);
    }

    public List<SseEnvelope> broadcasts(Predicate<SseEnvelope> envelope) {
        return envelopes.broadcasts(envelope);
    }

    public List<SseEnvelope> broadcasts(SseEventType type, Predicate<Object> payload) {
        return envelopes.broadcasts(type, payload);
    }

    public List<SseEnvelope> awaitUserEvents(Long userId, SseEventType type, Duration timeout) {
        return envelopes.await(() -> envelopes.userEvents(userId, type), timeout,
                "a " + type + " envelope for user " + userId);
    }

    public List<SseEnvelope> awaitCommentEvents(UUID plannerId, SseEventType type, Duration timeout) {
        return envelopes.await(() -> envelopes.commentEvents(plannerId, type), timeout,
                "a " + type + " envelope for planner " + plannerId);
    }

    public List<SseEnvelope> awaitBroadcasts(SseEventType type, Predicate<Object> payload, Duration timeout) {
        return envelopes.await(() -> envelopes.broadcasts(type, payload), timeout,
                "a matching " + type + " broadcast");
    }

    public void fence() {
        String marker = UUID.randomUUID().toString();
        CountDownLatch arrived = new CountDownLatch(1);
        fences.put(marker, arrived);
        try {
            fencePublisher.convertAndSend(fenceTopic, marker);
            if (!arrived.await(FENCE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("the recorder's fence marker did not come back within " + FENCE_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the recorder's fence", e);
        } finally {
            fences.remove(marker);
        }
    }

    @Override
    public void close() {
        try {
            container.stop();
            container.destroy();
        } catch (Exception e) {
            throw new IllegalStateException("could not close the SSE topic recorder", e);
        }
    }

    private void onMessage(Message message, byte[] pattern) {
        String topic = new String(message.getChannel(), StandardCharsets.UTF_8);
        if (topic.equals(fenceTopic)) {
            CountDownLatch arrived = fences.get(new String(message.getBody(), StandardCharsets.UTF_8));
            if (arrived != null) {
                arrived.countDown();
            }
            return;
        }
        SseChannel channel = SseChannel.fromTopic(topic);
        try {
            envelopes.record(channel, objectMapper.readValue(message.getBody(), SseEnvelope.class));
        } catch (IOException e) {
            throw new UncheckedIOException("unreadable SSE envelope on " + topic, e);
        }
    }

    static final class Envelopes {

        private record Recorded(SseChannel channel, SseEnvelope envelope) {
        }

        private final List<Recorded> recorded = new ArrayList<>();

        synchronized void record(SseChannel channel, SseEnvelope envelope) {
            recorded.add(new Recorded(channel, envelope));
            notifyAll();
        }

        List<SseEnvelope> userEvents(Long userId) {
            return matching(SseChannel.USER, envelope -> Objects.equals(envelope.userId(), userId));
        }

        List<SseEnvelope> userEvents(Long userId, SseEventType type) {
            return matching(SseChannel.USER, envelope -> envelope.type() == type
                    && Objects.equals(envelope.userId(), userId));
        }

        List<SseEnvelope> commentEvents(UUID plannerId) {
            return matching(SseChannel.COMMENT, envelope -> plannerId.toString().equals(envelope.plannerId()));
        }

        List<SseEnvelope> commentEvents(UUID plannerId, SseEventType type) {
            return matching(SseChannel.COMMENT, envelope -> envelope.type() == type
                    && plannerId.toString().equals(envelope.plannerId()));
        }

        List<SseEnvelope> broadcasts(Predicate<SseEnvelope> envelope) {
            return matching(SseChannel.BROADCAST, envelope);
        }

        List<SseEnvelope> broadcasts(SseEventType type, Predicate<Object> payload) {
            return matching(SseChannel.BROADCAST, envelope -> envelope.type() == type
                    && payload.test(envelope.payload()));
        }

        synchronized List<SseEnvelope> await(Supplier<List<SseEnvelope>> query, Duration timeout, String what) {
            long deadline = System.nanoTime() + timeout.toNanos();
            List<SseEnvelope> found = query.get();
            while (found.isEmpty()) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis <= 0) {
                    throw new AssertionError("waited " + timeout + " for " + what + "; none was published");
                }
                try {
                    wait(remainingMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted while waiting for " + what, e);
                }
                found = query.get();
            }
            return found;
        }

        private synchronized List<SseEnvelope> matching(SseChannel channel, Predicate<SseEnvelope> filter) {
            return recorded.stream()
                    .filter(entry -> entry.channel() == channel)
                    .map(Recorded::envelope)
                    .filter(filter)
                    .toList();
        }
    }
}
