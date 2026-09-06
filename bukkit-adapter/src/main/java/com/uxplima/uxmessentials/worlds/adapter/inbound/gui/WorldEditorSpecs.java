package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The shared spec loader the four world-editor menu screens use: it resolves a {@code modules/worlds/gui/world-*.conf}
 * disk-first then bundled, falling back to a closeable empty window so a typo or a missing file degrades cleanly rather
 * than aborting worlds wiring. Resolution mirrors {@code GuiLayouts} (disk first, then the classpath default), exactly
 * as each individual migrated menu's private loader does, but the four world-editor screens share one holder/listener
 * and are migrated as a coupled set, so they share one loader too rather than copying the boilerplate four times.
 */
@NullMarked
final class WorldEditorSpecs {

    private WorldEditorSpecs() {}

    /** Load the spec at {@code resource}, preferring an operator's on-disk edit, then bundled, then empty. */
    static MenuSpec load(String resource, int rows, Path dataFolder, Logger log) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        MenuSpecLoader specLoader = new MenuSpecLoader();
        Path onDisk = dataFolder.resolve(resource);
        if (Files.isRegularFile(onDisk)) {
            try {
                return specLoader.load(onDisk);
            } catch (MenuSpecException malformed) {
                log.error("failed to load menu spec " + onDisk + ", using bundled default", malformed);
            }
        }
        return loadBundled(specLoader, resource, rows, log);
    }

    private static MenuSpec loadBundled(MenuSpecLoader specLoader, String resource, int rows, Logger log) {
        try (InputStream in = WorldEditorSpecs.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("bundled menu spec {} is missing from the jar", resource);
                return empty(rows);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return specLoader.parse(reader.lines().collect(Collectors.joining("\n")));
            }
        } catch (IOException | MenuSpecException failure) {
            log.error("could not read bundled menu spec " + resource, failure);
            return empty(rows);
        }
    }

    /** A minimal valid spec used only when the real one cannot be read, so worlds still wires cleanly. */
    private static MenuSpec empty(int rows) {
        return new MenuSpec("", rows, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }
}
