package org.danteplanner.fixture.readresult;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.shared.failure.ReadResult;

public final class ReadResultFixtures {

    private ReadResultFixtures() {
    }

    @ReadResult
    public sealed interface Lookup {

        record Found(String name) implements Lookup {
        }

        record Missing() implements Lookup {
        }
    }

    public sealed interface Unmarked {

        record Found(String name) implements Unmarked {
        }

        record Missing() implements Unmarked {
        }
    }

    public static final class MarkedReader {

        private final ReadResultRepository repository;

        public MarkedReader(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Lookup lookup(String name) {
            return repository.findByName(name)
                    .<Lookup>map(row -> new Lookup.Found(row.name()))
                    .orElseGet(Lookup.Missing::new);
        }
    }

    public static final class MarkedWriter {

        private final ReadResultRepository repository;

        public MarkedWriter(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Lookup lookup(String name) {
            return repository.findByName(name)
                    .<Lookup>map(row -> new Lookup.Found(row.name()))
                    .orElseGet(() -> new Lookup.Found(repository.save(new ReadResultRepository.Row(name)).name()));
        }
    }

    public static final class MarkedLogger {

        private static final Logger LOG = LoggerFactory.getLogger(MarkedLogger.class);

        private final ReadResultRepository repository;

        public MarkedLogger(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Lookup lookup(String name) {
            LOG.info("looking up {}", name);
            return repository.findByName(name)
                    .<Lookup>map(row -> new Lookup.Found(row.name()))
                    .orElseGet(Lookup.Missing::new);
        }
    }

    public static final class MarkedModifier {

        private final ReadResultRepository repository;

        public MarkedModifier(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Lookup lookup(String name) {
            return repository.findAndTouch(name) == 0 ? new Lookup.Missing() : new Lookup.Found(name);
        }
    }

    public static final class UnmarkedReader {

        private final ReadResultRepository repository;

        public UnmarkedReader(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Unmarked lookup(String name) {
            return repository.findByName(name)
                    .<Unmarked>map(row -> new Unmarked.Found(row.name()))
                    .orElseGet(Unmarked.Missing::new);
        }
    }

    public static final class MarkedJdbcReader {

        private final JdbcTemplate jdbc;

        public MarkedJdbcReader(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public Lookup lookup(String name) {
            List<String> names = jdbc.query("SELECT name FROM rows WHERE name = ?",
                    (resultSet, rowNumber) -> resultSet.getString(1), name);
            return names.isEmpty() ? new Lookup.Missing() : new Lookup.Found(names.getFirst());
        }
    }

    public static final class MarkedJdbcWriter {

        private final JdbcTemplate jdbc;

        public MarkedJdbcWriter(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public Lookup lookup(String name) {
            return jdbc.update("DELETE FROM rows WHERE name = ?", name) == 0
                    ? new Lookup.Missing()
                    : new Lookup.Found(name);
        }
    }

    public static final class OptionReader {

        private final ReadResultRepository repository;

        public OptionReader(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            return Option.from(repository.findByName(name)).map(ReadResultRepository.Row::name);
        }
    }

    public static final class OptionWriter {

        private final ReadResultRepository repository;

        public OptionWriter(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            return new Option.Some<>(repository.findByName(name)
                    .orElseGet(() -> repository.save(new ReadResultRepository.Row(name))).name());
        }
    }

    public static final class OptionLogger {

        private static final Logger LOG = LoggerFactory.getLogger(OptionLogger.class);

        private final ReadResultRepository repository;

        public OptionLogger(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            LOG.info("looking up {}", name);
            return Option.from(repository.findByName(name)).map(ReadResultRepository.Row::name);
        }
    }

    public static final class QueryReader {

        private final ReadResultRepository repository;

        public QueryReader(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            return Option.from(repository.selectNamed(name)).map(ReadResultRepository.Row::name);
        }
    }

    public static final class DefaultMethodWriter {

        private final ReadResultRepository repository;

        public DefaultMethodWriter(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            return Option.from(repository.findOrSave(name)).map(ReadResultRepository.Row::name);
        }
    }

    public static final class ForUpdateReader {

        private final ReadResultRepository repository;

        public ForUpdateReader(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            return Option.from(repository.findLockedByName(name)).map(ReadResultRepository.Row::name);
        }
    }

    public static final class LockAnnotatedReader {

        private final ReadResultRepository repository;

        public LockAnnotatedReader(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            return Option.from(repository.findWithLockByName(name)).map(ReadResultRepository.Row::name);
        }
    }

    public static final class DerivedDeleter {

        private final ReadResultRepository repository;

        public DerivedDeleter(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            return repository.deleteByName(name) == 0 ? new Option.None<>() : new Option.Some<>(name);
        }
    }

    public static final class FragmentWriter {

        private final ReadResultRepository repository;

        public FragmentWriter(ReadResultRepository repository) {
            this.repository = repository;
        }

        public Option<String> lookup(String name) {
            return Option.from(repository.findOrCreate(name)).map(ReadResultRepository.Row::name);
        }
    }
}
