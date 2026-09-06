package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * A short-lived marker the worlds teleporter sets just before a staff-forced hand-off and the cross-world
 * access listener consumes on the resulting {@link org.bukkit.event.player.PlayerTeleportEvent}. It lets the
 * listener skip teleports the worlds module itself initiated. The staff {@code /worlds tp} override and the
 * restricted-login redirect, both routed through {@code WorldTeleportService.forced}, which deliberately
 * exempts staff hand-offs from the access policy, while still gating every other cross-world entry vector
 * ({@code /tp}, {@code /back}, ender pearls, third-party plugins) that the listener does not own.
 *
 * <p>Only the instant ADMIN hand-off is marked; the mark is removed the moment the listener observes the
 * paired event, so a normal cross-world teleport that happens to follow is gated as usual.
 */
@NullMarked
public final class ForcedWorldEntryMarker {

    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    /** Record that a worlds-initiated entry is about to be raised for {@code player}. */
    public void mark(UUID player) {
        pending.add(Objects.requireNonNull(player, "player"));
    }

    /** Removes and reports whether the player had a pending worlds-initiated entry. */
    public boolean consume(UUID player) {
        return pending.remove(Objects.requireNonNull(player, "player"));
    }
}
