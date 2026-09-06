package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit coverage of the {@link GuiLayouts} loader: an on-disk conf wins over the bundled default, a missing
 * conf falls back to the bundled classpath resource (and finally the code default), an unknown material name
 * degrades to the default material, and a malformed file logs and returns the code default rather than
 * throwing. No MockBukkit server is needed: the loader only parses HOCON and resolves {@link Material} names.
 */
class GuiLayoutsLoaderTest {

    private static final GuiLayout KITS_DEFAULT = GuiLayout.paginatedDefault(Material.CHEST);

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    @Test
    void anOnDiskConfWinsOverTheBundledDefault(@TempDir Path dir) throws Exception {
        writeGui(dir, "kits", "kits-menu", """
                rows = 3
                fallback-icon = "DIAMOND"
                nav-icon = "ARROW"
                prev-slot = 45
                next-slot = 53
                content-slots = [1, 2, 3]
                """);

        GuiLayout layout = new GuiLayouts(dir, NOOP).load("kits", "kits-menu", KITS_DEFAULT);

        assertThat(layout.rows()).isEqualTo(3);
        assertThat(layout.fallbackIcon()).isEqualTo(Material.DIAMOND);
        assertThat(layout.prevSlot()).isEqualTo(45);
        assertThat(layout.nextSlot()).isEqualTo(53);
        assertThat(layout.contentSlots()).containsExactly(1, 2, 3);
    }

    @Test
    void aMissingConfFallsBackToTheBundledClasspathResource(@TempDir Path dir) {
        // No on-disk file, so the loader reads the bundled modules/kits/gui/kits-menu.conf resource.
        GuiLayout layout = new GuiLayouts(dir, NOOP).load("kits", "kits-menu", KITS_DEFAULT);

        assertThat(layout.rows()).isEqualTo(6);
        assertThat(layout.fallbackIcon()).isEqualTo(Material.CHEST);
        assertThat(layout.prevSlot()).isEqualTo(48);
        assertThat(layout.nextSlot()).isEqualTo(50);
    }

    @Test
    void anUnknownModuleFallsBackToTheCodeDefault(@TempDir Path dir) {
        GuiLayout layout = new GuiLayouts(dir, NOOP).load("nope", "missing", KITS_DEFAULT);

        assertThat(layout).isEqualTo(KITS_DEFAULT);
    }

    @Test
    void anUnknownMaterialNameDegradesToTheDefaultMaterial(@TempDir Path dir) throws Exception {
        writeGui(dir, "kits", "kits-menu", "fallback-icon = \"NOT_A_REAL_MATERIAL\"\n");

        GuiLayout layout = new GuiLayouts(dir, NOOP).load("kits", "kits-menu", KITS_DEFAULT);

        assertThat(layout.fallbackIcon()).isEqualTo(Material.CHEST);
    }

    @Test
    void outOfRangeRowsDegradeToTheDefaultRows(@TempDir Path dir) throws Exception {
        writeGui(dir, "kits", "kits-menu", "rows = 9\n");

        GuiLayout layout = new GuiLayouts(dir, NOOP).load("kits", "kits-menu", KITS_DEFAULT);

        assertThat(layout.rows()).isEqualTo(6);
    }

    @Test
    void aMalformedConfReturnsTheCodeDefault(@TempDir Path dir) throws Exception {
        writeGui(dir, "kits", "kits-menu", "rows = = = oops {{{\n");

        GuiLayout layout = new GuiLayouts(dir, NOOP).load("kits", "kits-menu", KITS_DEFAULT);

        assertThat(layout).isEqualTo(KITS_DEFAULT);
    }

    @Test
    void anEmptyContentSlotsListFallsThroughToTheDefault(@TempDir Path dir) throws Exception {
        writeGui(dir, "kits", "kits-menu", "rows = 6\ncontent-slots = []\n");

        GuiLayout layout = new GuiLayouts(dir, NOOP).load("kits", "kits-menu", KITS_DEFAULT);

        assertThat(layout.contentSlots()).isEqualTo(List.of());
        assertThat(layout.explicitContentSlots()).isEmpty();
    }

    @Test
    void anEntityListConfDrivesGeometryFillerAndCreateButton(@TempDir Path dir) throws Exception {
        writeGui(dir, "demo", "demo-list", """
                rows = 6
                fallback-icon = "PAPER"
                nav-icon = "ARROW"
                prev-slot = 48
                next-slot = 50
                content-slots = [10, 11, 12]
                filler = "GRAY_STAINED_GLASS_PANE"
                create-slot = 49
                create-icon = "EMERALD"
                """);

        com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout layout = new GuiLayouts(dir, NOOP)
                .loadEntityList(
                        "demo",
                        "demo-list",
                        com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout.paginatedDefault(
                                Material.CHEST));

        assertThat(layout.rows()).isEqualTo(6);
        assertThat(layout.base().fallbackIcon()).isEqualTo(Material.PAPER);
        assertThat(layout.base().contentSlots()).containsExactly(10, 11, 12);
        assertThat(layout.filler()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(layout.createSlot()).hasValue(49);
        assertThat(layout.createIcon()).isEqualTo(Material.EMERALD);
    }

    @Test
    void anEntityListConfCanTurnOffTheCreateButtonWithANegativeSlot(@TempDir Path dir) throws Exception {
        writeGui(dir, "demo", "demo-list", "create-slot = -1\n");

        com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout layout = new GuiLayouts(dir, NOOP)
                .loadEntityList(
                        "demo",
                        "demo-list",
                        com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout.withCreate(
                                Material.CHEST, 49, Material.EMERALD));

        assertThat(layout.createSlot()).isEmpty();
    }

    @Test
    void anEntityEditorConfDrivesPropertySlotsAndButtons(@TempDir Path dir) throws Exception {
        writeGui(dir, "demo", "demo-editor", """
                rows = 3
                property-slots = [10, 12, 14, 16]
                back-slot = 22
                delete-slot = 26
                back-icon = "ARROW"
                delete-icon = "BARRIER"
                filler = "BLACK_STAINED_GLASS_PANE"
                """);

        com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout layout = new GuiLayouts(dir, NOOP)
                .loadEntityEditor(
                        "demo",
                        "demo-editor",
                        com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout.codeDefault(
                                List.of(0), 8));

        assertThat(layout.rows()).isEqualTo(3);
        assertThat(layout.propertySlots()).containsExactly(10, 12, 14, 16);
        assertThat(layout.backSlot()).isEqualTo(22);
        assertThat(layout.deleteSlot()).hasValue(26);
    }

    private static void writeGui(Path dir, String module, String name, String body) throws Exception {
        Path file = dir.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
    }
}
