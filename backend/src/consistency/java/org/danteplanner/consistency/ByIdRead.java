package org.danteplanner.consistency;

import java.util.List;

record ByIdRead(Operation operation, String session, String key, long invokedAt, long completedAt,
        ReadResult result) {

    static List<ByIdRead> all(History history) {
        return history.operations().stream()
                .<ByIdRead>mapMulti((operation, sink) -> {
                    switch (operation) {
                        case Read read -> sink.accept(new ByIdRead(
                                read, read.session(), read.key(), read.invokedAt(), read.completedAt(), read.result()));
                        case PublicRead read -> sink.accept(new ByIdRead(
                                read, read.session(), read.key(), read.invokedAt(), read.completedAt(), read.result()));
                        case Save save -> { }
                        case Delete delete -> { }
                        case ListRead list -> { }
                        case Publish publish -> { }
                        case Unpublish unpublish -> { }
                    }
                })
                .toList();
    }
}
