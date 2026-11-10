package org.danteplanner.consistency;

import java.util.List;

record PublishedVisibilityRead(Operation operation, String session, String key, long invokedAt, long completedAt,
        Seen seen) {

    enum Seen {
        SHOWN,
        MISSING,
        FAILED
    }

    static List<PublishedVisibilityRead> all(History history) {
        return history.operations().stream()
                .<PublishedVisibilityRead>mapMulti((operation, sink) -> {
                    switch (operation) {
                        case PublicRead read -> sink.accept(new PublishedVisibilityRead(read, read.session(),
                                read.key(), read.invokedAt(), read.completedAt(), seen(read.result())));
                        case ThreadRead read -> {
                            if (read.audience() == ThreadRead.Audience.PUBLIC) {
                                sink.accept(new PublishedVisibilityRead(read, read.session(), read.key(),
                                        read.invokedAt(), read.completedAt(), seen(read.result())));
                            }
                        }
                        case Save save -> { }
                        case Delete delete -> { }
                        case Read read -> { }
                        case ListRead list -> { }
                        case Publish publish -> { }
                        case Unpublish unpublish -> { }
                        case Deactivate deactivate -> { }
                        case Reactivate reactivate -> { }
                        case CommentWrite comment -> { }
                        case SettingsSave save -> { }
                        case SettingsRead read -> { }
                    }
                })
                .toList();
    }

    private static Seen seen(ReadResult result) {
        return switch (result) {
            case Found found -> Seen.SHOWN;
            case NotFound notFound -> Seen.MISSING;
            case ReadFailed failed -> Seen.FAILED;
        };
    }

    private static Seen seen(ThreadResult result) {
        return switch (result) {
            case Shown shown -> Seen.SHOWN;
            case NotFound notFound -> Seen.MISSING;
            case ReadFailed failed -> Seen.FAILED;
        };
    }
}
