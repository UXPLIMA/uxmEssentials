package com.uxplima.uxmessentials.poses.application;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownStartPhase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The one place the poses context consults the shared {@link Cooldowns} port, so all six pose starters gate against
 * a single rate limit rather than duplicating the wiring. The wait is a per-player tier read from the numbered
 * {@code uxmessentials.poses.cooldown.<seconds>} permission (the shortest matching tier wins, {@code 0}, the
 * default when a player holds no tier node. Means no wait), and {@code uxmessentials.poses.cooldown.bypass} skips
 * the gate entirely; the stamp is a transient PDC "ready-at" the adapter keeps. This mirrors how {@code /kit} and
 * the teleport verbs resolve their own {@code <seconds>} tiers through the same port.
 *
 * <p>Every pose shares one stamp scope ({@code "poses"}), so the cooldown is between <em>starting</em> poses of any
 * kind: sitting then immediately trying to lie down waits out the same clock. A start use case calls
 * {@link #remaining} before it commits (a present value is a refusal) and {@link #stamp} once the pose actually
 * begins, never on a refusal, so a denied attempt never burns the clock.
 */
public final class PoseCooldown {

    /**
     * One tier space, one stamp, keyed by the {@code poses} feature segment. The default is zero, absent a
     * {@code poses.cooldown.<seconds>} node a player waits for nothing. The start phase is irrelevant here (poses
     * stamp explicitly on a successful start, not through the teleport arrival machinery), so it takes the plain
     * default.
     */
    private static final CooldownKind KIND = new CooldownKind("poses", 0L, CooldownStartPhase.TELEPORT);

    private final @Nullable Cooldowns cooldowns;

    private PoseCooldown(@Nullable Cooldowns cooldowns) {
        this.cooldowns = cooldowns;
    }

    /** A gate backed by the real shared cooldown port. */
    public static PoseCooldown backedBy(Cooldowns cooldowns) {
        return new PoseCooldown(Objects.requireNonNull(cooldowns, "cooldowns"));
    }

    /** A gate that never delays and never stamps, the "no pose cooldown" policy (used where none is wired). */
    public static PoseCooldown unlimited() {
        return new PoseCooldown(null);
    }

    /**
     * How long {@code who} must still wait before starting a pose, or empty when they are ready now. A present
     * value is the amount of the {@link com.uxplima.uxmessentials.shared.application.message.SharedMessageKey#COOLDOWN_ACTIVE}
     * message the adapter renders.
     */
    public Optional<Duration> remaining(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (cooldowns == null) {
            return Optional.empty();
        }
        return cooldowns.check(who, KIND).asError();
    }

    /** Start the cooldown clock for {@code who}, sized by their resolved tier; a no-op for the unlimited gate. */
    public void stamp(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (cooldowns != null) {
            cooldowns.stamp(who, KIND);
        }
    }
}
