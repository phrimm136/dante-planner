package org.danteplanner.fixture.shellbranch;

import java.util.List;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellBranchFixtures {

    private static final Logger LOG = LoggerFactory.getLogger(ShellBranchFixtures.class);

    private final Port port;
    private final Queue<String> queue;
    private final List<String> items;

    public ShellBranchFixtures(Port port, Queue<String> queue, List<String> items) {
        this.port = port;
        this.queue = queue;
        this.items = items;
    }

    public void save(boolean x) {
        if (x) {
            port.save();
        }
    }

    public void drain() {
        while (queue.poll() != null) {
            port.send();
        }
    }

    public void sendEach() {
        for (String item : items) {
            port.send(item.isEmpty());
        }
    }

    public void drainWhenAllowed(boolean allowed) {
        while (queue.poll() != null) {
            if (allowed) {
                port.a();
            }
            port.send();
        }
    }

    public void drainEitherWay(boolean allowed) {
        while (queue.poll() != null) {
            if (allowed) {
                port.a();
            } else {
                port.b();
            }
        }
    }

    public void drainSkippingAfterA(boolean allowed) {
        while (queue.poll() != null) {
            if (allowed) {
                port.a();
                continue;
            }
            port.send();
        }
    }

    public void route(String kind) {
        switch (kind) {
            case "a" -> port.a();
            case "b" -> port.b();
            default -> port.send();
        }
    }

    public void drainAfterSending() {
        do {
            port.send();
        } while (queue.poll() != null);
    }

    public void act(Verdict v) {
        switch (v) {
            case A a -> port.a();
            case B b -> port.b();
        }
    }

    public void actGuarded(Verdict v, boolean x) {
        switch (v) {
            case A a when x -> port.a();
            case A a -> port.b();
            case B b -> port.b();
        }
    }

    public void f(boolean a, boolean b) {
        boolean c = a && b;
        port.send(c);
    }

    public void choose(boolean x, boolean y, boolean z) {
        port.send(x ? y : z);
    }

    public int max(int a, int b) {
        return a > b ? a : b;
    }

    public void guarded(boolean x, boolean y) {
        if (x || y) {
            return;
        }
        port.save();
    }

    public void guardedByAThrow(boolean x) {
        if (x) {
            throw new IllegalStateException("refused");
        }
        port.save();
    }

    public int guardReturningACall(boolean x) {
        if (x) {
            return port.count();
        }
        port.send();
        return 0;
    }

    public int guardReturningAConstant(boolean x) {
        if (x) {
            return 1;
        }
        port.send();
        return 0;
    }

    public void ternaryInAGuard(boolean x, boolean y) {
        if (x ? y : !y) {
            return;
        }
        port.save();
    }

    public void switchOnNull(String value) {
        switch (value) {
            case null -> port.a();
            default -> port.b();
        }
    }

    public void switchOnAPattern(Object value) {
        switch (value) {
            case String text -> port.send(text.isEmpty());
            default -> port.b();
        }
    }

    public void note(boolean x) {
        if (x) {
            LOG.info("noted");
        }
    }

    public interface Port {

        void save();

        void send();

        void send(boolean c);

        void a();

        void b();

        int count();
    }

    public sealed interface Verdict permits A, B {
    }

    public record A() implements Verdict {
    }

    public record B() implements Verdict {
    }

    public static class LateViolation {

        private final Port port;

        public LateViolation(Port port) {
            this.port = port;
        }

        public void late(boolean x) {
            if (x) {
                port.send();
            }
        }
    }
}
