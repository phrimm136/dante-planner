package org.danteplanner.logcapturefixture;

import org.danteplanner.backend.support.LogCapture;
import org.junit.jupiter.api.Test;

public class ScopedCaptureFixture {

    @Test
    void subject_WhenLogged_IsCapturedOnThisThread() {
        try (LogCapture capture = LogCapture.onThisThread(ScopedCaptureFixture.class)) {
            capture.events();
        }
    }
}
