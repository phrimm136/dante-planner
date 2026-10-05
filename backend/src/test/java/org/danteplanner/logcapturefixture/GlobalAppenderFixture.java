package org.danteplanner.logcapturefixture;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class GlobalAppenderFixture {

    @Test
    void subject_WhenLogged_IsCapturedGlobally() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        ((Logger) LoggerFactory.getLogger(GlobalAppenderFixture.class)).addAppender(appender);
    }

    @Test
    void subjectByReference_WhenLogged_IsCapturedGlobally() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        List.of(appender).forEach(((Logger) LoggerFactory.getLogger(GlobalAppenderFixture.class))::addAppender);
    }
}
