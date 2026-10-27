package org.danteplanner.backend.support;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;

import static org.assertj.core.api.Assertions.assertThat;

class LogCaptureTest {

    private static final Logger LOG = (Logger) LoggerFactory.getLogger(LogCaptureTest.class);

    @Test
    void onThisThread_WhenAnotherThreadLogsThroughTheSameLogger_KeepsOnlyThisThreadsEvents() throws Exception {
        try (LogCapture capture = LogCapture.onThisThread(LogCaptureTest.class)) {
            Thread neighbour = new Thread(() -> LOG.warn("neighbour"));
            neighbour.start();
            neighbour.join();
            LOG.warn("own");

            assertThat(capture.events()).extracting(ILoggingEvent::getFormattedMessage).containsExactly("own");
        }
    }

    @Test
    void onThread_WhenAThreadOfThatNameLogs_KeepsItsEvents() throws Exception {
        try (LogCapture capture = LogCapture.onThread("named-logger", LogCaptureTest.class)) {
            Thread named = new Thread(() -> LOG.warn("named"), "named-logger");
            named.start();
            named.join();

            assertThat(capture.events()).extracting(ILoggingEvent::getFormattedMessage).containsExactly("named");
        }
    }

    @Test
    void onThread_WhenOtherThreadsLog_DropsTheirEvents() throws Exception {
        try (LogCapture capture = LogCapture.onThread("named-logger", LogCaptureTest.class)) {
            Thread other = new Thread(() -> LOG.warn("other"), "other-logger");
            other.start();
            other.join();
            LOG.warn("test thread");

            assertThat(capture.events()).isEmpty();
        }
    }

    @Test
    void close_WhenCalled_DetachesFromTheLogger() {
        LogCapture capture = LogCapture.onThisThread(LogCaptureTest.class);
        capture.close();

        LOG.warn("after close");

        assertThat(capture.events()).isEmpty();
        assertThat(LOG.iteratorForAppenders()).isExhausted();
    }

    @Test
    void during_WhenTheActionLogsAtSeveralLevels_ReturnsOnlyTheRequestedLevel() {
        List<ILoggingEvent> warnings = LogCapture.during(() -> {
            LOG.warn("warned");
            LOG.error("failed");
        }, Level.WARN, LogCaptureTest.class);

        assertThat(warnings).extracting(ILoggingEvent::getFormattedMessage).containsExactly("warned");
    }

    @Test
    void onThisThread_WhenAContextStartsAndClosesMidCapture_KeepsCapturing() {
        try (LogCapture capture = LogCapture.onThisThread(LogCaptureTest.class)) {
            LOG.warn("before the first start");
            emptyContext().close();
            LOG.warn("after the first close");
            try (ConfigurableApplicationContext second = emptyContext()) {
                LOG.warn("while the second runs");
            }

            assertThat(capture.events()).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                    "before the first start", "after the first close", "while the second runs");
        }
    }

    private static ConfigurableApplicationContext emptyContext() {
        return new SpringApplicationBuilder(EmptyConfiguration.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .run();
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }
}
