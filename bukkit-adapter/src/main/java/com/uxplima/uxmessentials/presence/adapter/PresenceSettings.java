package com.uxplima.uxmessentials.presence.adapter;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.presence.domain.ActivityKind;
import com.uxplima.uxmessentials.presence.domain.ActivityPolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The presence context's typed config, read once from the module's scoped {@code modules.presence} subtree at
 * wiring time. It bounds the idle threshold (how long a player must be still before the sweep flips them to
 * AFK) and the sweep interval (how often the auto-AFK scan runs), and carries the round-3 anti-AFK matrix
 * the anti-machine activity filter, the disable-pickup-while-AFK toggle, and the sleep-ignores-AFK toggle
 * all additive and config-gated so an operator who declares nothing keeps the current behaviour.
 *
 * <p>A non-positive idle threshold is honoured by the domain as "never idle", so an operator switches auto-AFK
 * off by setting it to zero: manual {@code /afk} still works. The anti-machine toggles all default to the
 * inert value (empty ignore set, pickup-block off, sleep-ignore off), so enabling presence alone changes
 * nothing on the AFK-farm or sleep paths until an operator opts in.
 */
@NullMarked
public final class PresenceSettings {

    private static final long DEFAULT_IDLE_THRESHOLD_SECONDS = 300L;
    private static final long DEFAULT_SWEEP_SECONDS = 15L;
    private static final double DEFAULT_MOVE_THRESHOLD_BLOCKS = 0.0;

    private final Duration idleThreshold;
    private final Duration sweepInterval;
    private final ActivityPolicy activityPolicy;
    private final double moveThresholdBlocks;
    private final boolean disablePickupWhileAfk;
    private final boolean sleepIgnoresAfk;

    public PresenceSettings(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        this.idleThreshold =
                Duration.ofSeconds(config.getLong("afk-idle-threshold-seconds", DEFAULT_IDLE_THRESHOLD_SECONDS));
        this.sweepInterval = Duration.ofSeconds(config.getLong("afk-sweep-seconds", DEFAULT_SWEEP_SECONDS));
        this.activityPolicy = ActivityPolicy.ignoring(readIgnoredKinds(config));
        this.moveThresholdBlocks =
                Math.max(0.0, config.getDouble("anti-afk.move-threshold-blocks", DEFAULT_MOVE_THRESHOLD_BLOCKS));
        this.disablePickupWhileAfk = config.getBoolean("anti-afk.disable-pickup-while-afk", false);
        this.sleepIgnoresAfk = config.getBoolean("anti-afk.sleep-ignores-afk", false);
    }

    /** How long a player must be idle before the auto-AFK sweep flips them to AFK. */
    public Duration idleThreshold() {
        return idleThreshold;
    }

    /** How often the auto-AFK sweep scans the presence map. */
    public Duration sweepInterval() {
        return sweepInterval;
    }

    /** The anti-machine activity filter: which classified activity kinds still reset the idle clock. */
    public ActivityPolicy activityPolicy() {
        return activityPolicy;
    }

    /**
     * The horizontal distance, in blocks, a position change must exceed to count as a real {@code MOVE}; a
     * change at or below it is classified as sub-threshold {@code ROTATE}. Zero keeps the legacy block-hop rule.
     */
    public double moveThresholdBlocks() {
        return moveThresholdBlocks;
    }

    /** Whether an AFK player's item pickups are cancelled (default off). */
    public boolean disablePickupWhileAfk() {
        return disablePickupWhileAfk;
    }

    /** Whether AFK and vanished players are excluded from the sleep-percentage so the rest can pass the night. */
    public boolean sleepIgnoresAfk() {
        return sleepIgnoresAfk;
    }

    private static Set<ActivityKind> readIgnoredKinds(ConfigStore config) {
        EnumSet<ActivityKind> ignored = EnumSet.noneOf(ActivityKind.class);
        for (String token : config.getStringList("anti-afk.ignored-activity", List.of())) {
            ActivityKind.fromToken(token).ifPresent(ignored::add);
        }
        return ignored;
    }
}
