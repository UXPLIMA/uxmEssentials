package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultResourcesLayoutTest {

    @Test
    void firstRunExtractsThePerModuleTree(@TempDir Path dir) {
        DefaultResources.writeInto(dir, Logger.getLogger("test"), "test");
        assertThat(Files.exists(dir.resolve("config.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/teleport/config.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/teleport/rtp.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/economy/currencies.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/communication/config.conf")))
                .isTrue();
        assertThat(Files.exists(dir.resolve("modules/communication/join-quit.conf")))
                .isTrue();
        assertThat(Files.exists(dir.resolve("modules/communication/announcer.conf")))
                .isTrue();
        assertThat(Files.exists(dir.resolve("modules/communication/advancements.conf")))
                .isTrue();
        assertThat(Files.exists(dir.resolve("modules/communication/info-pages.conf")))
                .isTrue();
        assertThat(Files.exists(dir.resolve("modules/holograms/config.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/playerwarps/config.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("modules/scoreboard/config.conf"))).isTrue();
        assertThat(Files.exists(dir.resolve("messages/messages_en.conf"))).isTrue();
    }

    /**
     * Every operator-editable config resource that ships in the jar must appear in the first-run extraction
     * list, otherwise a newly added module's config stays buried in the jar and is unconfigurable on disk.
     * Walks the bundled resource tree and diffs the discovered {@code modules/**} configs and the message
     * catalogs against {@link DefaultResources#files()}, failing on any resource not listed.
     */
    @Test
    void everyBundledModuleConfigIsExtractedOnFirstRun() {
        Path resources = repoRoot().resolve("bukkit-adapter/src/main/resources");
        assertThat(Files.isDirectory(resources))
                .as("expected bundled resources under %s", resources)
                .isTrue();

        Set<String> listed = Set.copyOf(DefaultResources.files());
        List<String> missing;
        try (Stream<Path> walk = Files.walk(resources)) {
            missing = walk.filter(Files::isRegularFile)
                    .map(p -> resources.relativize(p).toString().replace('\\', '/'))
                    .filter(DefaultResourcesLayoutTest::isOperatorEditable)
                    .filter(rel -> !listed.contains(rel))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        assertThat(missing)
                .as(
                        "bundled config resources missing from DefaultResources.FILES (add them so first-run "
                                + "extraction makes them editable on disk):\n%s",
                        String.join("\n", missing))
                .isEmpty();
    }

    /** A {@code .conf} resource an operator is meant to edit: a per-module config or a message catalog. */
    private static boolean isOperatorEditable(String relative) {
        if (!relative.endsWith(".conf")) {
            return false;
        }
        if (relative.startsWith("messages/") || relative.startsWith("input/")) {
            return true;
        }
        return relative.startsWith("modules/")
                && (relative.endsWith("/config.conf") || relative.contains("/gui/") || isNamedModuleConfig(relative));
    }

    /** A non-{@code config.conf} sibling config (e.g. {@code rtp.conf}, {@code currencies.conf}) under a module. */
    private static boolean isNamedModuleConfig(String relative) {
        // modules/<module>/<file>.conf: exactly two separators, so the file is not under a gui/ subfolder.
        return relative.chars().filter(c -> c == '/').count() == 2;
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
