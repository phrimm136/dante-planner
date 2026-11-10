package org.danteplanner.consistency;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadShowsCommentTest {

    private static final CommentWrite EDITED = new CommentWrite("s1", "p1", "c1", 2, 10, 20, new Changed());

    @Test
    void thread_WhenItStartsAfterTheEditResponseAndShowsAnOlderRevision_IsAViolation() {
        ThreadRead read = thread("p1", 21, 30, new Shown(Map.of("c1", 1L), Set.of()));

        assertThat(violations(EDITED, read))
                .containsExactly(new Violation("thread-after-comment-write", "s2", "p1", read));
    }

    @Test
    void thread_WhenItShowsTheWrittenRevisionOrALaterOne_IsClean() {
        assertThat(violations(EDITED,
                thread("p1", 21, 30, new Shown(Map.of("c1", 2L), Set.of())),
                thread("p1", 21, 30, new Shown(Map.of("c1", 3L), Set.of())))).isEmpty();
    }

    @Test
    void thread_WhenTheCommentIsAbsent_IsAViolation() {
        ThreadRead read = thread("p1", 21, 30, new Shown(Map.of(), Set.of()));

        assertThat(violations(EDITED, read))
                .containsExactly(new Violation("thread-after-comment-write", "s2", "p1", read));
    }

    @Test
    void thread_WhenTheCommentIsMaskedAndNoDeactivationHappened_IsAViolation() {
        ThreadRead read = thread("p1", 21, 30, new Shown(Map.of(), Set.of("c1")));

        assertThat(violations(EDITED, read))
                .containsExactly(new Violation("thread-after-comment-write", "s2", "p1", read));
    }

    @Test
    void thread_WhenTheCommentIsMaskedWhileADeactivationCompletedAndNoReactivationDid_IsClean() {
        assertThat(violations(EDITED, new Deactivate("s1", 15, 18, new Changed()),
                thread("p1", 21, 30, new Shown(Map.of(), Set.of("c1"))))).isEmpty();
    }

    @Test
    void thread_WhenTheCommentIsMaskedAfterAReactivationCompletedBeforeTheReadStarted_IsAViolation() {
        ThreadRead read = thread("p1", 41, 50, new Shown(Map.of(), Set.of("c1")));

        assertThat(violations(EDITED, new Deactivate("s1", 15, 18, new Changed()),
                new Reactivate("s1", 25, 35, new Changed()), read))
                .containsExactly(new Violation("thread-after-comment-write", "s2", "p1", read));
    }

    @Test
    void thread_WhenTheCommentIsMaskedWhileTheReactivationIsStillInFlight_IsClean() {
        assertThat(violations(EDITED, new Deactivate("s1", 15, 18, new Changed()),
                new Reactivate("s1", 25, 45, new Changed()),
                thread("p1", 41, 50, new Shown(Map.of(), Set.of("c1"))))).isEmpty();
    }

    @Test
    void thread_WhenTheCommentIsMaskedAfterAnUnknownDeactivationAndNoReactivation_IsClean() {
        assertThat(violations(EDITED, new Deactivate("s1", 15, 18, new Unknown()),
                thread("p1", 21, 30, new Shown(Map.of(), Set.of("c1"))))).isEmpty();
    }

    @Test
    void thread_WhenTheCommentIsBothShownAndMasked_IsTreatedAsMasked() {
        assertThat(violations(EDITED, new Deactivate("s1", 15, 18, new Changed()),
                thread("p1", 21, 30, new Shown(Map.of("c1", 1L), Set.of("c1"))))).isEmpty();
    }

    @Test
    void thread_WhenTheWriteOutcomeIsUnknown_IsNotJudged() {
        assertThat(violations(new CommentWrite("s1", "p1", "c1", 2, 10, 20, new Unknown()),
                thread("p1", 21, 30, new Shown(Map.of(), Set.of())))).isEmpty();
    }

    @Test
    void thread_WhenItStartsAtTheEditCompletion_IsNotJudged() {
        assertThat(violations(EDITED, thread("p1", 20, 30, new Shown(Map.of("c1", 1L), Set.of())))).isEmpty();
    }

    @Test
    void thread_WhenTheWriteWasRejected_IsClean() {
        assertThat(violations(new CommentWrite("s1", "p1", "", 1, 10, 20, new Rejected(403)),
                thread("p1", 21, 30, new Shown(Map.of(), Set.of())))).isEmpty();
    }

    @Test
    void thread_WhenNotFound_IsClean() {
        assertThat(violations(EDITED, thread("p1", 21, 30, new NotFound()))).isEmpty();
    }

    @Test
    void thread_WhenTheWriteWasOnAnotherPlanner_IsClean() {
        assertThat(violations(EDITED, thread("p2", 21, 30, new Shown(Map.of(), Set.of())))).isEmpty();
    }

    @Test
    void thread_WhenTwoWritesAreMissing_IsOneViolation() {
        ThreadRead read = thread("p1", 41, 50, new Shown(Map.of(), Set.of()));

        assertThat(violations(EDITED, new CommentWrite("s3", "p1", "c2", 1, 30, 40, new Changed()), read))
                .containsExactly(new Violation("thread-after-comment-write", "s2", "p1", read));
    }

    @Test
    void thread_WhenAPublicReaderMissesTheWrittenRevision_IsAViolation() {
        ThreadRead read = new ThreadRead("anon", "p1", ThreadRead.Audience.PUBLIC, 21, 30,
                new Shown(Map.of("c1", 1L), Set.of()));

        assertThat(violations(EDITED, read))
                .containsExactly(new Violation("thread-after-comment-write", "anon", "p1", read));
    }

    private static ThreadRead thread(String key, long invokedAt, long completedAt, ThreadResult result) {
        return new ThreadRead("s2", key, ThreadRead.Audience.OWNER, invokedAt, completedAt, result);
    }

    private static List<Violation> violations(Operation... operations) {
        Verdict verdict = new ThreadShowsComment().judge(History.of(List.of(operations)));
        assertThat(verdict.anomalies()).isEmpty();
        return verdict.violations();
    }
}
