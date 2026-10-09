package org.danteplanner.consistency;

import java.util.OptionalLong;

final class Results {

    private Results() {
    }

    static OptionalLong appliedVersion(SaveResult result) {
        return switch (result) {
            case Applied applied -> OptionalLong.of(applied.syncVersion());
            case Rejected rejected -> OptionalLong.empty();
            case Unknown unknown -> OptionalLong.empty();
        };
    }

    static OptionalLong foundVersion(ReadResult result) {
        return switch (result) {
            case Found found -> OptionalLong.of(found.syncVersion());
            case NotFound notFound -> OptionalLong.empty();
            case ReadFailed failed -> OptionalLong.empty();
        };
    }

    static boolean deleted(DeleteResult result) {
        return switch (result) {
            case Deleted deleted -> true;
            case Rejected rejected -> false;
            case Unknown unknown -> false;
        };
    }

    static boolean mayHaveDeleted(DeleteResult result) {
        return switch (result) {
            case Deleted deleted -> true;
            case Rejected rejected -> false;
            case Unknown unknown -> true;
        };
    }
}
