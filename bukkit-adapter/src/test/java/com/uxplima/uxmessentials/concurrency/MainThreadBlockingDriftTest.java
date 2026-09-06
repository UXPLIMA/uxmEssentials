package com.uxplima.uxmessentials.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The main-thread-blocking guard (CLAUDE.md §3, docs/05-testing §16).
 *
 * <p>Command and listener handlers run on the server tick / region thread. CLAUDE.md requires every one of
 * them to return control within its budget and push all I/O off-thread through the injected {@code Scheduler}
 * port. This guard scans production source under {@code adapter/inbound/command/} and
 * {@code adapter/inbound/listener/} and fails if a handler reaches for a blocking primitive directly instead
 * of delegating it: {@code Thread.sleep(...)}, raw JDBC ({@code java.sql.*} / {@code DriverManager} /
 * {@code getConnection(...)}), or a synchronous HTTP client ({@code java.net.http.*} / {@code HttpClient}).
 * Database work belongs in the persistence adapter behind a repository port; HTTP belongs in an outbound
 * adapter invoked through {@code Scheduler.async}; neither has any business on the inbound (tick) thread.
 *
 * <p><strong>Scope and honest limits.</strong> This is a line-level source scan of the inbound layer, not a
 * call-graph analysis. It catches the blocking <em>primitives</em> named above where they appear directly in a
 * command or listener class, which is where they would actually stall a tick. It deliberately does NOT try to
 * flag a blocking {@code future.get()} / {@code future.join()}: {@code get(}/{@code join(} collide with
 * {@code List.get}, {@code Optional.get}, {@code Map.get}, {@code String.join}, {@code Collectors.joining}, so a
 * line regex cannot tell a blocking future call from an innocent one without a flood of false positives. A
 * blocking future call on the tick thread therefore stays a review-time concern (CLAUDE.md §3 lists it as
 * "no guard, thread-context-dependent"). Nor does it follow a handler into a helper it delegates to; the
 * primitives it does catch are the ones that, in practice, leak into the inbound layer.
 *
 * <p>A genuinely-safe exception can be annotated with a trailing {@code // allow-blocking: <reason>} comment on
 * the offending line, which the scan skips. There are none today; the inbound layer is clean.
 */
class MainThreadBlockingDriftTest {

    /** Blocking primitives that must never appear directly in an inbound (tick-thread) command or listener. */
    private static final Pattern BLOCKING = Pattern.compile("\\bThread\\.sleep\\s*\\("
            + "|\\bDriverManager\\b"
            + "|\\bjava\\.sql\\."
            + "|\\.getConnection\\s*\\("
            + "|\\bjava\\.net\\.http\\."
            + "|\\bHttpClient\\b"
            // getOfflinePlayer(String) costs a Mojang round-trip for an uncached name on an online-mode server.
            // The IfCached variant is the deliberate non-blocking read and does not match this alternative.
            + "|\\bgetOfflinePlayer\\s*\\(");

    /** A line carrying this trailing marker is a reviewed, justified exception and is not flagged. */
    private static final String ALLOW_MARKER = "allow-blocking:";

    @Test
    void inboundHandlersDoNotBlockTheTickThread() {
        List<String> violations = new ArrayList<>();
        for (Path root : sourceRoots()) {
            scan(root, violations);
        }
        assertThat(violations)
                .as(
                        "command/listener handlers run on the tick thread; push I/O off-thread through the "
                                + "Scheduler port instead of blocking (CLAUDE.md §3):\n%s",
                        String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void blockingPatternSelfTest() {
        // Examples the guard MUST flag.
        assertThat(matches("Thread.sleep(50L);")).isTrue();
        assertThat(matches("var c = DriverManager.getConnection(url);")).isTrue();
        assertThat(matches("import java.sql.PreparedStatement;")).isTrue();
        assertThat(matches("HttpClient client = HttpClient.newHttpClient();")).isTrue();
        assertThat(matches("import java.net.http.HttpResponse;")).isTrue();
        // Examples the guard MUST NOT flag: ordinary collection/optional/string calls and the Scheduler path.
        assertThat(matches("var home = homes.get(index);")).isFalse();
        assertThat(matches("String joined = String.join(\", \", names);")).isFalse();
        assertThat(matches("scheduler.async(() -> repository.find(name));")).isFalse();
        assertThat(matches("Optional<Npc> npc = view.find(name);")).isFalse();
    }

    private static boolean matches(String line) {
        return BLOCKING.matcher(line).find();
    }

    private void scan(Path root, List<String> violations) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(MainThreadBlockingDriftTest::isInboundHandler)
                    .forEach(path -> scanFile(path, violations));
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to walk " + root, failure);
        }
    }

    /** Only the inbound (tick-thread) layer: the command and listener handler packages. */
    private static boolean isInboundHandler(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/adapter/inbound/command/") || normalized.contains("/adapter/inbound/listener/");
    }

    private void scanFile(Path path, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + path, failure);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.stripLeading().startsWith("//") || line.contains(ALLOW_MARKER)) {
                continue;
            }
            if (BLOCKING.matcher(line).find()) {
                violations.add(path + ":" + (i + 1) + "  " + line.strip());
            }
        }
    }

    /** The {@code src/main/java} root of the bukkit adapter, the only module with an inbound handler layer. */
    private static List<Path> sourceRoots() {
        Path src = repoRoot().resolve("bukkit-adapter").resolve("src/main/java");
        assertThat(Files.isDirectory(src))
                .as("expected the bukkit-adapter production source root at %s", src)
                .isTrue();
        return List.of(src);
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
