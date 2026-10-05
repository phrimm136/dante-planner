package org.danteplanner.backend.support;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public final class LogCapture implements AutoCloseable {

    private final Thread owner = Thread.currentThread();
    private final List<Logger> loggers;
    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            if (Thread.currentThread() == owner) {
                events.add(event);
            }
        }
    };

    private LogCapture(Class<?>... loggerOwners) {
        this.loggers = Arrays.stream(loggerOwners)
                .map(loggerOwner -> (Logger) LoggerFactory.getLogger(loggerOwner))
                .toList();
        appender.start();
        loggers.forEach(logger -> logger.addAppender(appender));
    }

    public static LogCapture onThisThread(Class<?>... loggerOwners) {
        return new LogCapture(loggerOwners);
    }

    public static List<ILoggingEvent> during(Runnable action, Level level, Class<?>... loggerOwners) {
        try (LogCapture capture = onThisThread(loggerOwners)) {
            action.run();
            return capture.at(level);
        }
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(events);
    }

    public List<ILoggingEvent> at(Level level) {
        return events.stream().filter(event -> event.getLevel() == level).toList();
    }

    @Override
    public void close() {
        loggers.forEach(logger -> logger.detachAppender(appender));
        appender.stop();
    }
}
