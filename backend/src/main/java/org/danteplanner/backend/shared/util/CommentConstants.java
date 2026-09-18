package org.danteplanner.backend.shared.util;

public final class CommentConstants {

    public static final int CONTENT_MAX_LENGTH = 10000;

    /**
     * Capped at the TINYINT column ceiling.
     */
    public static final int MAX_DEPTH = 127;

    public static final String DELETED_CONTENT = "";

    private CommentConstants() {
    }
}
