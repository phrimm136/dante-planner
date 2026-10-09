package org.danteplanner.consistency;

public final class HistoryFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    HistoryFormatException(int line, String reason) {
        super("line " + line + ": " + reason);
    }

    HistoryFormatException(int line, String reason, Throwable cause) {
        super("line " + line + ": " + reason, cause);
    }
}
