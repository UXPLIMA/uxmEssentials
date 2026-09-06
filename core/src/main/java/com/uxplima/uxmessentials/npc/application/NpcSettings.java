package com.uxplima.uxmessentials.npc.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * A typed read view over the npc module's {@code modules/npc/config.conf} subtree. The look-at-player feature is
 * per-NPC (the domain {@code lookAtPlayer} flag, toggled by {@code /npc lookatplayer}); the two settings here are
 * the cross-cutting knobs that apply to every looking NPC: how close a viewer must be before the NPC turns to
 * face them, and how often the look loop re-aims. Every getter carries a sensible default so a minimal (or
 * absent) config still yields working behaviour.
 *
 * <p>The store is the module-scoped config (rooted at {@code modules.npc}); the keys below are relative to that
 * root. A reload simply reads a fresh view from the swapped {@link ConfigStore}.
 */
public final class NpcSettings {

    private static final double DEFAULT_LOOK_RANGE = 12.0;
    private static final long DEFAULT_LOOK_PERIOD_TICKS = 3;
    private static final long DEFAULT_CLICK_COOLDOWN_MILLIS = 500;
    private static final String DEFAULT_MINESKIN_API_KEY = "";
    private static final long DEFAULT_NPC_LIMIT = -1;

    private final ConfigStore config;

    public NpcSettings(ConfigStore config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** The radius within which a looking NPC turns to face a viewer; default 12 blocks, never negative. */
    public double lookRange() {
        return Math.max(0.0, config.getDouble("look-at-player.range", DEFAULT_LOOK_RANGE));
    }

    /** How often the look loop re-aims every looking NPC at its nearby viewers; default 3 ticks (150 ms). */
    public Duration lookPeriod() {
        long ticks = Math.max(1L, config.getLong("look-at-player.tick-period", DEFAULT_LOOK_PERIOD_TICKS));
        return Duration.ofMillis(ticks * 50L);
    }

    /** The per-player-per-NPC cooldown that swallows a duplicate click; default 500 ms, never negative. */
    public Duration clickCooldown() {
        long millis = Math.max(0L, config.getLong("click-cooldown-millis", DEFAULT_CLICK_COOLDOWN_MILLIS));
        return Duration.ofMillis(millis);
    }

    /**
     * The optional MineSkin API key used to generate signed skins from an image URL ({@code /npc skin ... url:}).
     * Empty by default. The unauthenticated public tier works for occasional skinning; a key raises the rate
     * limit and is sent as an {@code Authorization: Bearer} header. The returned value is stripped, so surrounding
     * whitespace in the config never reaches the header.
     */
    public String mineSkinApiKey() {
        return config.getString("skin.mineskin-api-key", DEFAULT_MINESKIN_API_KEY)
                .strip();
    }

    /**
     * The default number of NPCs a player may own without a {@code uxmessentials.npc.limit.<n>} node; {@code -1}
     * (the default) means unlimited, so an operator opts into capping via the config value or the numbered nodes.
     * Clamped to {@code -1} at minimum so a stray smaller value cannot break the quota reducer.
     */
    public int defaultLimit() {
        return (int) Math.max(-1L, config.getLong("default-limit", DEFAULT_NPC_LIMIT));
    }

    /**
     * The command labels an NPC's bound command or click action may not run (case-insensitive, matched on the
     * first word). Empty by default: nothing is blocked, so a server that configures no list behaves as before.
     */
    public List<String> blockedCommands() {
        return config.getStringList("blocked-commands", List.of());
    }
}
