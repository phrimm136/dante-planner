package org.danteplanner.consistency;

import java.util.List;

public record ListRead(String session, long invokedAt, long completedAt, List<String> keys) implements Operation {

    public ListRead {
        keys = List.copyOf(keys);
    }
}
