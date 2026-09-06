package com.uxplima.uxmessentials.playerstate.adapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.WorldCommandPolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * Typed view over the world-policy slice of {@code playerstate.conf}: the per-world command blocker map
 * ({@code world-command-blocks}) and the no-fly worlds list ({@code no-fly-worlds}). Read once at wire time
 * from the module's scoped config so the policies are fixed for the lifetime of the wiring; an operator
 * changes them via a module reload, which re-wires the context and re-reads this view.
 *
 * <p>The command-block map is a HOCON map keyed by world name (plus the {@code "*"} wildcard), each value a
 * list of command labels. It is read by enumerating the child keys under {@code world-command-blocks} and
 * pulling each world's string list; an empty map yields a policy that blocks nothing and reports empty so the
 * listener short-circuits. The no-fly list is a plain string list; an empty list disables the no-fly feature
 * entirely.
 */
@NullMarked
public final class PlayerstateSettings {

    private static final String COMMAND_BLOCKS = "world-command-blocks";
    private static final String NO_FLY_WORLDS = "no-fly-worlds";
    private static final String PLAYTIME_TRACKING = "playtime.tracking";
    private static final String PLAYTIME_SAMPLE_SECONDS = "playtime.sample-seconds";

    /** Default sampling cadence in seconds; floored to one so a mis-set value never schedules a zero-delay loop. */
    private static final int DEFAULT_SAMPLE_SECONDS = 60;

    private final WorldCommandPolicy worldCommandPolicy;
    private final List<String> noFlyWorlds;
    private final boolean playtimeTracking;
    private final int playtimeSampleSeconds;

    public PlayerstateSettings(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        this.worldCommandPolicy = new WorldCommandPolicy(readCommandBlocks(config));
        this.noFlyWorlds = config.getStringList(NO_FLY_WORLDS, List.of());
        this.playtimeTracking = config.getBoolean(PLAYTIME_TRACKING, true);
        this.playtimeSampleSeconds = Math.max(1, config.getInt(PLAYTIME_SAMPLE_SECONDS, DEFAULT_SAMPLE_SECONDS));
    }

    /** Whether the AFK-aware playtime sampler runs (the {@code /playtime} breakdown is empty when off). */
    public boolean playtimeTracking() {
        return playtimeTracking;
    }

    /** The sampling cadence in seconds: how much playtime each tick credits each online player. */
    public int playtimeSampleSeconds() {
        return playtimeSampleSeconds;
    }

    /** The per-world command-block rule built from {@code world-command-blocks}. */
    public WorldCommandPolicy worldCommandPolicy() {
        return worldCommandPolicy;
    }

    /** The configured no-fly world names, verbatim (case-folding is left to the policy). */
    public List<String> noFlyWorlds() {
        return noFlyWorlds;
    }

    private static Map<String, List<String>> readCommandBlocks(ConfigStore config) {
        Map<String, List<String>> blocks = new LinkedHashMap<>();
        for (String world : config.getKeys(COMMAND_BLOCKS)) {
            blocks.put(world, config.getStringList(COMMAND_BLOCKS + "." + world, List.of()));
        }
        return blocks;
    }
}
