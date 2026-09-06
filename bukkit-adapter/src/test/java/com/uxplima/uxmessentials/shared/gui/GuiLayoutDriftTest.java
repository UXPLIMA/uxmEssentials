package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards the externalised browse-menu layouts: every menu that reads a {@code modules/<m>/gui/<name>.conf}
 * must ship that resource under {@code src/main/resources}, it must parse to the row count today's code
 * default carries, and it must hold layout/material keys only. Never a {@code MessageKey} or any localised
 * string. Titles and lore stay in the message catalog, so a layout file that smuggled player text would split
 * the translation surface across two files and silently diverge from the locale-parity guard.
 */
class GuiLayoutDriftTest {

    @Test
    void everyBrowseMenuShipsItsLayoutResourceWithTheExpectedRows() {
        assertRows("kits", "kits-menu", 6);
        assertRows("homes", "icon-selector", 6);
        assertRows("itemworld", "disposal", 6);
        assertRows("kits", "kits-preview", 6);
    }

    @Test
    void layoutResourcesDeclareNoLocalisedTextKeys() {
        // The comments may mention "title"/"lore" in prose (to point operators at the catalog); what must never
        // appear is a config key that would carry player text, a "title"/"lore"/"name" assignment or a
        // MessageKey reference. Player text stays in the message catalog, asserted by the locale-parity guard. A
        // key like a rename-slot legitimately contains "name", so the check targets the assignment form
        // (key = value) rather than the bare substring.
        for (String[] menu : new String[][] {
            {"kits", "kits-menu"},
            {"homes", "icon-selector"},
            {"itemworld", "disposal"},
            {"kits", "kits-preview"}
        }) {
            String body = read(menu[0], menu[1]);
            assertThat(stripComments(body))
                    .as(menu[0] + "/" + menu[1] + " declares no localised-text key")
                    .doesNotContain("MessageKey")
                    .doesNotContain("title =")
                    .doesNotContain("lore =")
                    .doesNotContain("name =");
        }
    }

    /** Drop {@code #} comment lines so the assertion only sees the declared HOCON keys, not the prose. */
    private static String stripComments(String body) {
        StringBuilder kept = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            if (!line.strip().startsWith("#")) {
                kept.append(line).append('\n');
            }
        }
        return kept.toString();
    }

    private void assertRows(String module, String name, int expected) {
        String body = read(module, name);
        assertThat(body)
                .as(module + "/" + name + " declares rows = " + expected)
                .contains("rows = " + expected);
    }

    private String read(String module, String name) {
        Path file = guiResource(module, name);
        assertThat(file).as(module + "/" + name + " gui resource").exists();
        try {
            return Files.readString(file);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("could not read " + file, failure);
        }
    }

    private static Path guiResource(String module, String name) {
        return resources().resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
    }

    private static Path resources() {
        return repoRoot().resolve("bukkit-adapter/src/main/resources");
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
