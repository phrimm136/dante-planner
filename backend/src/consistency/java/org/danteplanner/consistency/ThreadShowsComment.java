package org.danteplanner.consistency;

import java.util.List;

final class ThreadShowsComment implements Rule {

    static final String NAME = "thread-after-comment-write";

    @Override
    public Verdict judge(History history) {
        List<Violation> violations = history.all(ThreadRead.class).stream()
                .filter(read -> read.result() instanceof Shown shown
                        && history.all(CommentWrite.class).stream()
                                .filter(write -> write.key().equals(read.key()))
                                .filter(write -> Results.changed(write.result()))
                                .filter(write -> read.invokedAt() > write.completedAt())
                                .anyMatch(write -> misses(shown, write, read, history)))
                .map(read -> new Violation(NAME, read.session(), read.key(), read))
                .toList();
        return new Verdict(violations, List.of());
    }

    private static boolean misses(Shown shown, CommentWrite write, ThreadRead read, History history) {
        if (shown.masked().contains(write.comment())) {
            return !deactivationMayHold(read, history);
        }
        Long revision = shown.revisions().get(write.comment());
        return revision == null || revision < write.revision();
    }

    private static boolean deactivationMayHold(ThreadRead read, History history) {
        return history.all(Deactivate.class).stream()
                .filter(deactivate -> Results.mayHaveChanged(deactivate.result()))
                .filter(deactivate -> deactivate.invokedAt() < read.completedAt())
                .anyMatch(deactivate -> history.all(Reactivate.class).stream()
                        .filter(reactivate -> Results.changed(reactivate.result()))
                        .noneMatch(reactivate -> reactivate.invokedAt() > deactivate.completedAt()
                                && reactivate.completedAt() < read.invokedAt()));
    }
}
