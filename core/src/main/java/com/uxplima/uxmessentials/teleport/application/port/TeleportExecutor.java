package com.uxplima.uxmessentials.teleport.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;

/**
 * Outbound port that issues the actual region-aware async teleport. The adapter implements this over
 * Paper's {@code teleportAsync} from the player's entity scheduler, retaining passengers/vehicles and
 * applying the post-teleport invulnerability grace window per config. The application hands it a
 * resolved {@link Destination} (which may follow a live target) and a {@link TeleportKind} for the audit
 * verb; it never touches a Bukkit {@code Location} itself.
 *
 * <p>The call is the single point where the entity hop happens. It is reached only after every gate
 * (permission, cooldown, warmup) has passed, so by contract no I/O precedes it on the tick thread.
 */
public interface TeleportExecutor {

    /**
     * Teleport {@code who} to {@code destination}, attributing the hop to {@code kind}. The adapter
     * captures the pre-teleport position for {@code /back}, performs the region hop off the tick thread,
     * and publishes {@code PlayerTeleported} on arrival.
     */
    void teleport(PlayerRef who, Destination destination, TeleportKind kind);

    /**
     * The same hop, but run {@code onLanded} on the player's region thread once the async teleport resolves
     * to an actual landing: never on a refusal (Paper returning {@code false}) or an error. This is the
     * single seam the gated flow uses so the cooldown stamp, the RTP charge, and the arrival grace fire only
     * after the player truly arrives.
     *
     * <p>The default treats a dispatched teleport as landed, which suits executors that cannot report an
     * arrival (test fakes, simple positional hops); the real async executor overrides it to invoke
     * {@code onLanded} only when {@code teleportAsync} completes {@code true}.
     */
    default void teleport(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
        teleport(who, destination, kind);
        onLanded.run();
    }

    /**
     * Perform an involuntary relocation without creating a {@code /back} point. Join-spawn and first-join RTP use
     * this path: logging in must not overwrite the last meaningful teleport/death location. Simple test executors
     * fall back to their normal hop; the production adapter overrides this with an untracked async teleport.
     */
    default void relocate(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
        teleport(who, destination, kind, onLanded);
    }
}
