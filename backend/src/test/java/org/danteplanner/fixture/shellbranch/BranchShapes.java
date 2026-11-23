package org.danteplanner.fixture.shellbranch;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;

import org.danteplanner.fixture.shellbranch.ShellBranchFixtures.Port;

public class BranchShapes<T extends Comparable<T>> {

    private final Port port;
    private final Queue<String> queue;

    public BranchShapes(Port port, Queue<String> queue, boolean ready) {
        this.port = port;
        this.queue = queue;
        if (ready) {
            port.a();
        }
    }

    public void inALambda(List<String> items) {
        items.forEach(item -> {
            if (item.isEmpty()) {
                port.a();
            }
        });
    }

    public Runnable inAnAnonymousClass(boolean x) {
        return new Runnable() {
            @Override
            public void run() {
                if (x) {
                    port.a();
                }
            }
        };
    }

    public Supplier<Runnable> inAnAnonymousClassInALambda(boolean x) {
        return () -> new Runnable() {
            @Override
            public void run() {
                if (x) {
                    port.a();
                }
            }
        };
    }

    public void endingALoopBody(List<String> items, boolean x) {
        for (String item : items) {
            port.send(item.isEmpty());
            if (x) {
                port.a();
            }
        }
    }

    public void breakingOutOfALoop(List<String> items, boolean x) {
        for (String item : items) {
            if (x) {
                break;
            }
            port.send(item.isEmpty());
        }
    }

    public void termsOfALoopCondition(boolean x) {
        while (x && queue.poll() != null) {
            port.send();
        }
    }

    public void numberLabels(int number) {
        switch (number) {
            case -1, 1 -> port.a();
            default -> port.b();
        }
    }

    public void enumLabels(Level level) {
        switch (level) {
            case null -> port.save();
            case LOW -> port.a();
            case HIGH -> port.b();
        }
    }

    public boolean comparison(int a, int b) {
        port.send();
        return a == b;
    }

    public void overloaded(String one) {
        if (one.isEmpty()) {
            port.a();
        }
    }

    public void overloaded(String[] many) {
        if (many.length == 0 || many[0].isEmpty()) {
            port.a();
        }
    }

    public void overloaded(int[][] grid) {
        if (grid.length == 0 || grid[0].length == 0 || grid[0][0] == 0) {
            port.a();
        }
    }

    public void overloaded(T bounded) {
        if (bounded != null && bounded.hashCode() == 0 && queue.isEmpty() || queue.size() == 2) {
            port.a();
        }
    }

    public <U> void overloaded(U unbounded, Map.Entry<String, List<U>> entry) {
        if (unbounded == null) {
            port.a();
        }
    }

    public void varargs(String... many) {
        if (many.length == 0) {
            port.a();
        }
    }

    public void localClass(boolean captured) {
        class Local {

            Local(int size) {
                if (captured && size > 0) {
                    port.a();
                }
            }

            void run() {
                if (captured) {
                    port.b();
                }
            }
        }
        new Local(1).run();
    }

    public static void localClassInAStaticMethod(Port given, boolean captured) {
        class StaticLocal {

            StaticLocal(String name) {
                if (captured) {
                    given.a();
                }
            }
        }
        new StaticLocal("name");
    }

    public <U> void localClassTakingATypeVariable(U given) {
        class TypedLocal {

            TypedLocal(U value) {
                if (value == null) {
                    port.a();
                }
            }
        }
        new TypedLocal(given);
    }

    public enum EffectfulTwins {
        ONE {
            @Override
            void run(Port given, boolean x) {
                if (x) {
                    given.a();
                }
            }
        },
        OTHER {
            @Override
            void run(Port given, boolean x) {
                if (x && given != null) {
                    given.b();
                }
            }
        };

        abstract void run(Port given, boolean x);
    }

    public enum PureTwins {
        ONE {
            @Override
            int size(boolean x) {
                return x ? 1 : 0;
            }
        },
        OTHER {
            @Override
            int size(boolean x) {
                return 0;
            }
        };

        abstract int size(boolean x);
    }

    public static class FieldAndMethod {

        private final Port port;
        private final Queue<String> queue;
        private final Runnable inAField = new Runnable() {
            @Override
            public void run() {
                if (queue.isEmpty()) {
                    port.a();
                }
            }
        };

        public FieldAndMethod(Port port, Queue<String> queue) {
            this.port = port;
            this.queue = queue;
        }

        public Runnable inAMethod(boolean x) {
            return new Runnable() {
                @Override
                public void run() {
                    if (x) {
                        port.b();
                    }
                }
            };
        }

        public void runTheField() {
            inAField.run();
        }
    }

    public class Inner {

        public Inner(boolean x) {
            if (x) {
                port.a();
            }
        }
    }

    public enum Level {
        LOW(null),
        HIGH(null);

        Level(Port given) {
            if (given != null) {
                given.a();
            }
        }
    }

    public record Compact(Port given, List<String> items) {

        public Compact {
            if (items.isEmpty()) {
                given.a();
            }
        }
    }
}
