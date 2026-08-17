package org.danteplanner.backend.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds the {@code /api/internal} surface closed.
 *
 * <p>Those endpoints mutated one pod's memory in place: the change reached whichever replica served
 * the request, survived no restart, and left no audit trail. Game data now arrives through a deploy
 * and the rotation flag through configuration, so nothing needs the surface — and its security
 * exemptions (an authentication bypass and a CSRF exemption keyed on the path prefix) must not
 * linger for a future endpoint to inherit silently.</p>
 */
class InternalSurfaceRemovedTest {

    private static final String INTERNAL_PREFIX = "/api/internal";
    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");

    @Test
    @DisplayName("no production source or configuration mentions the internal API surface")
    void internalEndpointsRemoved_WhenSourcesScanned_NoInternalSurfaceRemains() throws IOException {
        List<String> offenders = Stream.concat(walk(MAIN_SOURCES), walk(MAIN_RESOURCES))
                .filter(InternalSurfaceRemovedTest::mentionsInternalSurface)
                .map(Path::toString)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("the internal API surface is removed; a request to %s must reach no handler, "
                        + "and no authentication or CSRF exemption may survive it", INTERNAL_PREFIX)
                .isEmpty();
    }

    private static Stream<Path> walk(Path root) throws IOException {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        return Files.walk(root).filter(Files::isRegularFile);
    }

    private static boolean mentionsInternalSurface(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(INTERNAL_PREFIX);
        } catch (IOException | java.io.UncheckedIOException e) {
            return false;
        }
    }
}
