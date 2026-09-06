package com.uxplima.uxmessentials.nametags.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The nametags context's operator content, loaded once at wiring time from {@code modules/nametags/config.conf} and
 * held in an {@link AtomicReference} so a reload swaps a fresh parse whole. Readers see either the previous or the
 * new content, never a half-applied tree (CLAUDE.md "swapped atomically via AtomicReference on reload"). An absent or
 * unreadable file yields the inert default, so a server that enables the module without authoring the file renders no
 * nametag.
 *
 * <p>The {@link #formats()} and {@link #refreshInterval()} suppliers read the live parse on each call, so a reload
 * takes effect on the next render tick with no re-wiring. The {@link #animations()} definitions replace the live
 * {@code AnimationRegistry} on reload. The content is
 * operator data the renderer parses through MiniMessage and the placeholder pipeline; nothing here is a
 * {@code MessageKey} or parity-checked.
 */
@NullMarked
public final class NametagSettings {

    private static final String CONTENT_FILE = "config.conf";

    private final Path moduleDir;
    private final Logger log;
    private final AtomicReference<NametagContentCodec.Parsed> parsed;

    public NametagSettings(Path moduleDir, Logger log) {
        this.moduleDir = Objects.requireNonNull(moduleDir, "moduleDir");
        this.log = Objects.requireNonNull(log, "log");
        this.parsed = new AtomicReference<>(NametagContentCodec.read(load(moduleDir, log, false), log));
    }

    /** The live named-format set; read fresh by the renderer each tick to select a format per wearer. */
    public NametagConfig formats() {
        return Objects.requireNonNull(parsed.get(), "parsed").formats();
    }

    /** The live animation definitions, used to replace the stateful registry on reload. */
    public List<AnimationDef> animations() {
        return Objects.requireNonNull(parsed.get(), "parsed").animations();
    }

    /** The live global refresh interval the render timer re-reads each reschedule. */
    public Duration refreshInterval() {
        return Objects.requireNonNull(parsed.get(), "parsed").refreshInterval();
    }

    /** Whether a wearer's vanilla above-head name is hidden while their custom nametag is live; read fresh per show. */
    public boolean hideVanillaName() {
        return Objects.requireNonNull(parsed.get(), "parsed").hideVanillaName();
    }

    /** Re-read the config file and swap the parsed content atomically. */
    public void reload() {
        parsed.set(NametagContentCodec.read(load(moduleDir, log, true), log));
    }

    private static ConfigurationNode load(Path moduleDir, Logger log, boolean failOnReadError) {
        Path file = moduleDir.resolve(CONTENT_FILE);
        if (!Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return HoconConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException failure) {
            log.error("failed to load " + file + "; nametags runs inert", failure);
            if (failOnReadError) {
                throw new IllegalStateException("failed to parse nametags config " + file, failure);
            }
            return CommentedConfigurationNode.root();
        }
    }
}
