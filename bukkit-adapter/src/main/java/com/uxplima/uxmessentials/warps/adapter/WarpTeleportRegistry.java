package com.uxplima.uxmessentials.warps.adapter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The handoff between a warp teleport launch and the arrival listener that plays the welcome message, sounds
 * and particles. The teleporter records a {@link PendingWarpNotification} for the player when it launches the
 * hop; the arrival listener consumes it once the player lands. State is per-player and short-lived (one entry
 * survives only between launch and arrival), so this is a single instance owned by the warps module wiring,
 * shared with the player-warps teleporter and the arrival listener, and drained on module stop, never a
 * static global. Ownership: the {@link ConcurrentHashMap} is mutated only through {@code register} /
 * {@code getAndRemove} / {@code clear}, all of which are thread-safe.
 */
@NullMarked
public final class WarpTeleportRegistry {

    private final Map<UUID, PendingWarpNotification> pending = new ConcurrentHashMap<>();

    public void register(UUID player, Warp warp) {
        pending.put(
                player,
                new PendingWarpNotification(
                        warp.name().value(),
                        warp.welcomeMessages(),
                        warp.departureSound(),
                        warp.arrivalSound(),
                        warp.departureParticle(),
                        warp.arrivalParticle(),
                        warp.iconMaterial()));
    }

    public void register(UUID player, PlayerWarp warp) {
        // Player warps no longer carry welcome messages (dropped in the surrogate-id rebuild), so the arrival
        // listener plays only the warp's effects and icon; the welcome list is empty on this path.
        pending.put(
                player,
                new PendingWarpNotification(
                        warp.name().value(),
                        java.util.List.of(),
                        warp.effects().departureSound(),
                        warp.effects().arrivalSound(),
                        warp.effects().departureParticle(),
                        warp.effects().arrivalParticle(),
                        warp.icon().map(IconSpec::value)));
    }

    public Optional<PendingWarpNotification> getAndRemove(UUID player) {
        return Optional.ofNullable(pending.remove(player));
    }

    /** Drop every pending notification; called on warps module stop so no per-player state outlives it. */
    public void clear() {
        pending.clear();
    }

    public record PendingWarpNotification(
            String name,
            java.util.List<WelcomeMessage> welcomeMessages,
            Optional<String> departureSound,
            Optional<String> arrivalSound,
            Optional<String> departureParticle,
            Optional<String> arrivalParticle,
            Optional<String> iconMaterial) {}
}
