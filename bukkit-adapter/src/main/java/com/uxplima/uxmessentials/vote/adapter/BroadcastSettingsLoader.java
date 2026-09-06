package com.uxplima.uxmessentials.vote.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Sound;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.vote.adapter.outbound.BukkitVoteBroadcaster.BroadcastDisplay;
import com.uxplima.uxmessentials.vote.application.BroadcastSettings;
import com.uxplima.uxmessentials.vote.domain.BroadcastType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Parses the {@code broadcast { ... }} block of {@code modules/vote/config.conf} into the core
 * {@link BroadcastSettings} the vote use case reads and the adapter-side {@link BroadcastDisplay} the
 * {@link BukkitVoteBroadcaster} renders with. Every field is tolerant: an unknown {@code type} falls back
 * to {@link BroadcastType#EVERY_VOTE}, an unknown {@link BroadcastChannel} or boss-bar colour/overlay is
 * skipped or replaced with its default, and an unresolvable sound name yields no sound.
 *
 * <p><b>Back-compat:</b> the pre-V5b config carried a scalar boolean {@code broadcast = true|false}. When
 * the node is a scalar rather than a section it is mapped to a type, {@code true} to
 * {@link BroadcastType#EVERY_VOTE}, {@code false} to {@link BroadcastType#NONE}, with the default channel
 * set and display, so an un-migrated config keeps working unchanged.
 */
@NullMarked
public final class BroadcastSettingsLoader {

    private static final int DEFAULT_TITLE_FADE_IN_MS = 500;
    private static final int DEFAULT_TITLE_STAY_MS = 2000;
    private static final int DEFAULT_TITLE_FADE_OUT_MS = 500;
    private static final long DEFAULT_BOSS_BAR_SECONDS = 4;

    private BroadcastSettingsLoader() {}

    /** The parsed pair: the core settings the use case reads and the display the broadcaster renders with. */
    public record Loaded(BroadcastSettings settings, BroadcastDisplay display) {
        public Loaded {
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(display, "display");
        }
    }

    /** Read {@code moduleConfig} and parse its {@code broadcast} block; an absent file yields the defaults. */
    public static Loaded loadFrom(Path moduleConfig, Logger log) {
        Objects.requireNonNull(moduleConfig, "moduleConfig");
        Objects.requireNonNull(log, "log");
        if (!Files.exists(moduleConfig)) {
            return defaults();
        }
        try {
            ConfigurationNode root = HoconConfigurationLoader.builder()
                    .path(moduleConfig)
                    .build()
                    .load();
            return parse(root.node("broadcast"));
        } catch (ConfigurateException failure) {
            log.error("failed to load vote broadcast settings from " + moduleConfig + "; using defaults", failure);
            return defaults();
        }
    }

    /** Parse the {@code broadcast} node; an absent node or a legacy scalar boolean is handled here. */
    public static Loaded parse(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        if (node.virtual()) {
            return defaults();
        }
        if (!node.isMap()) {
            // Legacy scalar boolean: true -> EVERY_VOTE, false -> NONE, default channels/display.
            BroadcastType type = node.getBoolean(true) ? BroadcastType.EVERY_VOTE : BroadcastType.NONE;
            BroadcastSettings settings =
                    new BroadcastSettings(type, Duration.ZERO, Set.of(BroadcastChannel.CHAT), Set.of());
            return new Loaded(settings, defaultDisplay());
        }
        BroadcastSettings settings = new BroadcastSettings(
                parseType(node.node("type")),
                Duration.ofSeconds(Math.max(0, node.node("cooldown-seconds").getInt(0))),
                parseChannels(node.node("channels")),
                parseBlacklist(node.node("hidden-voters")));
        return new Loaded(settings, parseDisplay(node));
    }

    private static BroadcastType parseType(ConfigurationNode node) {
        String raw = node.getString("");
        try {
            return BroadcastType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return BroadcastType.EVERY_VOTE;
        }
    }

    private static Set<BroadcastChannel> parseChannels(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return Set.of(BroadcastChannel.CHAT);
        }
        Set<BroadcastChannel> channels = new LinkedHashSet<>();
        for (ConfigurationNode child : node.childrenList()) {
            @Nullable String raw = child.getString();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                channels.add(BroadcastChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                // Skip an unknown channel rather than failing the whole load.
            }
        }
        return channels.isEmpty() ? Set.of(BroadcastChannel.CHAT) : Set.copyOf(channels);
    }

    private static Set<String> parseBlacklist(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return Set.of();
        }
        List<String> names = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            @Nullable String raw = child.getString();
            if (raw != null && !raw.isBlank()) {
                names.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(names);
    }

    private static BroadcastDisplay parseDisplay(ConfigurationNode node) {
        ConfigurationNode title = node.node("title");
        ConfigurationNode bossBar = node.node("boss-bar");
        @Nullable Sound sound = BukkitRegistryKeys.resolveSound(node.node("sound").getString(""));
        return new BroadcastDisplay(
                Math.max(0, title.node("fade-in-ms").getInt(DEFAULT_TITLE_FADE_IN_MS)),
                Math.max(0, title.node("stay-ms").getInt(DEFAULT_TITLE_STAY_MS)),
                Math.max(0, title.node("fade-out-ms").getInt(DEFAULT_TITLE_FADE_OUT_MS)),
                BroadcastDisplay.color(bossBar.node("color").getString("PURPLE")),
                BroadcastDisplay.overlay(bossBar.node("overlay").getString("PROGRESS")),
                Math.max(1, bossBar.node("seconds").getInt((int) DEFAULT_BOSS_BAR_SECONDS)),
                sound);
    }

    private static Loaded defaults() {
        BroadcastSettings settings =
                new BroadcastSettings(BroadcastType.EVERY_VOTE, Duration.ZERO, Set.of(BroadcastChannel.CHAT), Set.of());
        return new Loaded(settings, defaultDisplay());
    }

    private static BroadcastDisplay defaultDisplay() {
        return new BroadcastDisplay(
                DEFAULT_TITLE_FADE_IN_MS,
                DEFAULT_TITLE_STAY_MS,
                DEFAULT_TITLE_FADE_OUT_MS,
                BroadcastDisplay.color("PURPLE"),
                BroadcastDisplay.overlay("PROGRESS"),
                DEFAULT_BOSS_BAR_SECONDS,
                null);
    }
}
