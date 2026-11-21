package org.danteplanner.backend.shared.util;

import java.util.regex.Pattern;

/**
 * A client-supplied value that goes into a log line carries no CR or LF, so the console
 * PatternLayout cannot be made to print a forged line.
 */
public final class LogValues {

    private static final Pattern LINE_BREAKS = Pattern.compile("[\r\n]");
    private static final String LINE_BREAK_REPLACEMENT = "_";

    private LogValues() {
    }

    public static String oneLine(String clientValue) {
        if (clientValue == null) {
            return null;
        }
        return LINE_BREAKS.matcher(clientValue).replaceAll(LINE_BREAK_REPLACEMENT);
    }
}
