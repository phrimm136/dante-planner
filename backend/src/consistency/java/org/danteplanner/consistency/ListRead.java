package org.danteplanner.consistency;

import java.util.List;

public record ListRead(String session, Listing listing, long invokedAt, long completedAt, int page, int size,
        long totalElements, List<String> keys) implements Operation {

    public enum Listing {
        OWNER,
        CATALOG
    }

    public ListRead {
        keys = List.copyOf(keys);
    }

    boolean whole() {
        return page == 0 && totalElements <= size;
    }
}
