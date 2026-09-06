package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The text-input seam's operator config, loaded once at wiring time from {@code text-input.conf} and held in an
 * {@link AtomicReference} so a reload swaps a fresh parse whole. A reader sees either the previous or the new
 * content, never a half-applied tree (CLAUDE.md "swapped atomically via AtomicReference on reload"). The file
 * carries the global {@code default-mode}, the per-key {@code modes} overrides, and the {@code cancel-keywords}; an
 * absent or unreadable file yields the shipped defaults (anvil everywhere, {@code cancel}/{@code iptal}) so a server
 * that never authors the file still has a working, cancellable input flow.
 *
 * <p>{@link #modeFor(String)}, {@link #cancelKeywords()} and {@link #isCancel(String)} read the live parse on each
 * call, so a reload takes effect on the next prompt with no re-wiring.
 */
@NullMarked
public final class InputSettings {

    private final Path configFile;
    private final Logger log;
    private final AtomicReference<InputContentCodec.Parsed> parsed;

    /** @param configFile the {@code text-input.conf} file under the data folder */
    public InputSettings(Path configFile, Logger log) {
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        this.log = Objects.requireNonNull(log, "log");
        this.parsed = new AtomicReference<>(InputContentCodec.read(load(configFile, log, false), log));
    }

    /** The configured mode for {@code key}, its per-key override if set, otherwise the global default. */
    public InputMode modeFor(String key) {
        Objects.requireNonNull(key, "key");
        return current().modeFor(key);
    }

    /** The live cancel-keyword list both backends accept (lower-cased comparison is the caller's job). */
    public List<String> cancelKeywords() {
        return current().cancelKeywords();
    }

    /** True when {@code input} (trimmed, case-insensitive) matches one of the configured cancel keywords. */
    public boolean isCancel(String input) {
        Objects.requireNonNull(input, "input");
        String trimmed = input.trim();
        for (String keyword : current().cancelKeywords()) {
            if (trimmed.equalsIgnoreCase(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** The first configured cancel keyword, shown to chat-mode players as the hint of what to type to abort. */
    public String primaryCancelKeyword() {
        List<String> keywords = current().cancelKeywords();
        return keywords.isEmpty() ? "cancel" : keywords.get(0).toLowerCase(Locale.ROOT);
    }

    /** Re-read the config file and swap the parsed content atomically. */
    public void reload() {
        parsed.set(InputContentCodec.read(load(configFile, log, true), log));
    }

    private InputContentCodec.Parsed current() {
        return Objects.requireNonNull(parsed.get(), "parsed");
    }

    private static ConfigurationNode load(Path file, Logger log, boolean failOnReadError) {
        if (!Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return HoconConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException failure) {
            log.error(
                    "failed to load " + file + "; text input falls back to anvil with default cancel keywords",
                    failure);
            if (failOnReadError) {
                throw new IllegalStateException("failed to parse text-input config " + file, failure);
            }
            return CommentedConfigurationNode.root();
        }
    }
}
