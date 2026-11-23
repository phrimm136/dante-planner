package org.danteplanner.backend.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.micrometer.core.instrument.MeterRegistry;

import org.danteplanner.backend.shared.gtid.GtidWriteCapture;

final class RecordingGtidWriteCapture extends GtidWriteCapture {

    private final ThreadLocal<List<Capture>> taken = ThreadLocal.withInitial(ArrayList::new);

    private RecordingGtidWriteCapture(MeterRegistry meterRegistry) {
        super(meterRegistry);
    }

    @Override
    public Optional<Capture> takeCapture() {
        Optional<Capture> capture = super.takeCapture();
        capture.ifPresent(taken.get()::add);
        return capture;
    }

    void forget() {
        taken.remove();
    }

    List<Capture> taken() {
        return List.copyOf(taken.get());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @Primary
        RecordingGtidWriteCapture recordingGtidWriteCapture(MeterRegistry meterRegistry) {
            return new RecordingGtidWriteCapture(meterRegistry);
        }
    }
}
