package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.util.Locale;
import java.util.Objects;

import net.kyori.adventure.bossbar.BossBar;

import org.jspecify.annotations.NullMarked;

/**
 * The adapter-side display timing a {@link ChannelBroadcaster} applies to its non-chat surfaces: the title
 * fade-in / stay / fade-out in milliseconds, and the boss-bar colour, overlay, and visible-seconds before it is
 * hidden. It carries no sound. The sound is passed per broadcast to {@link ChannelBroadcaster#broadcast} so one
 * shared broadcaster can play a different sound per message (a per-announcement sound, a per-vote sound).
 *
 * <p>The colour and overlay are pre-resolved by the loader through the tolerant {@link #color}/{@link #overlay}
 * helpers, so an unknown name in config degrades to {@code PURPLE}/{@code PROGRESS} rather than failing the load.
 * The vote and communication contexts each parse their own config block into this same record.
 *
 * @param titleFadeInMs the title fade-in in milliseconds
 * @param titleStayMs the title stay in milliseconds
 * @param titleFadeOutMs the title fade-out in milliseconds
 * @param bossBarColor the boss-bar colour
 * @param bossBarOverlay the boss-bar overlay
 * @param bossBarSeconds how long the boss bar stays visible before it is hidden (at least one)
 */
@NullMarked
public record ChannelDisplay(
        int titleFadeInMs,
        int titleStayMs,
        int titleFadeOutMs,
        BossBar.Color bossBarColor,
        BossBar.Overlay bossBarOverlay,
        long bossBarSeconds) {

    public ChannelDisplay {
        Objects.requireNonNull(bossBarColor, "bossBarColor");
        Objects.requireNonNull(bossBarOverlay, "bossBarOverlay");
        if (bossBarSeconds < 1) {
            bossBarSeconds = 1;
        }
    }

    /** Resolve a {@link BossBar.Color} name tolerant of case; unknown names fall back to {@code PURPLE}. */
    public static BossBar.Color color(String name) {
        try {
            return BossBar.Color.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return BossBar.Color.PURPLE;
        }
    }

    /** Resolve a {@link BossBar.Overlay} name tolerant of case; unknown names fall back to {@code PROGRESS}. */
    public static BossBar.Overlay overlay(String name) {
        try {
            return BossBar.Overlay.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return BossBar.Overlay.PROGRESS;
        }
    }
}
