package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The no-inline-literals guard (CLAUDE.md §3, docs/13-i18n §10.1, docs/05-testing §16).
 *
 * <p>Scans production source under {@code domain/} / {@code application/} / {@code adapter/} in both
 * {@code :core} and {@code :bukkit-adapter} and bans inline user-facing string literals passed to a
 * player-facing primitive, {@code sendMessage("…")}, {@code sendRichMessage("…")},
 * {@code Component.text("…")}, or {@code String.format("…")}. Every user-visible string must resolve
 * through a {@link com.uxplima.uxmessentials.shared.application.message.MessageKey} so the per-player
 * locale and the {@code en} fallback chain both apply.
 *
 * <p>Operator-facing SLF4J logger lines stay literal (docs/13-i18n §1). The boundary is the
 * {@code MessageSink} / {@code messages.resolve} call, not the logger, so a {@code log.info("…")} is
 * never matched. The guard ships its own {@code regexPatternsSelfTest} so its detection patterns are
 * covered by an example that must match and an example that must not.
 */
class NoInlineUserMessageDriftTest {

    /** A string literal passed straight into a player-facing send / component / format primitive. */
    private static final Pattern INLINE_LITERAL =
            Pattern.compile("(?:sendMessage|sendRichMessage|sendActionBar)\\s*\\(\\s*\""
                    + "|Component\\.text\\s*\\(\\s*\""
                    + "|String\\.format\\s*\\(\\s*\"");

    private static final List<String> SCANNED_PACKAGES = List.of("domain", "application", "adapter");

    @Test
    void productionSourceHasNoInlineUserFacingLiterals() {
        List<String> violations = new ArrayList<>();
        for (Path root : sourceRoots()) {
            scan(root, violations);
        }
        assertThat(violations)
                .as(
                        "inline player-facing literals must resolve through a MessageKey (docs/13-i18n §10.1):\n%s",
                        String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void regexPatternsSelfTest() {
        // Examples the guard MUST flag.
        assertThat(matches("player.sendMessage(\"You have no home named \" + name);"))
                .isTrue();
        assertThat(matches("sender.sendRichMessage(\"<red>Denied.\");")).isTrue();
        assertThat(matches("return Component.text(\"You don't have permission.\");"))
                .isTrue();
        assertThat(matches("String.format(\"Paid %s to %s\", amount, name);")).isTrue();
        // Examples the guard MUST NOT flag: a resolve call, a constant ref, and a logger line.
        assertThat(matches("sink.deliver(viewer, messages.resolve(viewer, KEY, ph));"))
                .isFalse();
        assertThat(matches("sender.sendMessage(Component.text(STATUS_HEADER));"))
                .isFalse();
        assertThat(matches("log.info(\"module {} loaded\", id);")).isFalse();
        assertThat(matches("Component.text(label + state)")).isFalse();
    }

    private static boolean matches(String line) {
        return INLINE_LITERAL.matcher(line).find();
    }

    private void scan(Path root, List<String> violations) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(NoInlineUserMessageDriftTest::isScannedPackage)
                    .forEach(path -> scanFile(path, violations));
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to walk " + root, failure);
        }
    }

    private static boolean isScannedPackage(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return SCANNED_PACKAGES.stream().anyMatch(pkg -> normalized.contains("/" + pkg + "/"));
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
            Matcher matcher = INLINE_LITERAL.matcher(line);
            if (matcher.find() && !line.stripLeading().startsWith("//")) {
                violations.add(path + ":" + (i + 1) + "  " + line.strip());
            }
        }
    }

    /** The {@code src/main/java} roots of the two production modules, located from the repo root. */
    private static List<Path> sourceRoots() {
        Path repoRoot = repoRoot();
        List<Path> roots = new ArrayList<>();
        for (String module : List.of("core", "bukkit-adapter")) {
            Path src = repoRoot.resolve(module).resolve("src/main/java");
            if (Files.isDirectory(src)) {
                roots.add(src);
            }
        }
        assertThat(roots)
                .as("expected at least one production source root under %s", repoRoot)
                .isNotEmpty();
        return roots;
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
