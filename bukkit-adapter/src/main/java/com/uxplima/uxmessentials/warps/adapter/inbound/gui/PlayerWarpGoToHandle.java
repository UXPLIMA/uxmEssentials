package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A late-bound handle to the player-warp "go to" teleport the shared warp editor invokes when its teleport
 * button is clicked on a player warp. The warps module is wired before player-warps, so the editor listener
 * cannot receive the {@code UsePlayerWarp} use case in its constructor. It receives this handle (always
 * non-null, constructor-injected and {@code final}), and the player-warps wiring binds the teleport into it once
 * that context is wired. When player-warps is disabled the handle stays empty and {@link #teleport} is a no-op,
 * so the editor simply does nothing for a player warp it can no longer resolve.
 *
 * <p>Ownership: the action is written exactly once by the player-warps wiring on enable and read on the editor's
 * entity thread; the {@link AtomicReference} makes that publication safe across threads. The bound action sends
 * the viewer to the named warp owned by {@code owner} through the same {@code UsePlayerWarp} path the
 * {@code /pwarp <owner> <name>} command takes.
 */
@NullMarked
public final class PlayerWarpGoToHandle {

    /** The teleport a viewer to {@code owner}'s warp {@code name}, bound by the player-warps wiring. */
    @FunctionalInterface
    public interface GoTo {
        void teleport(PlayerRef viewer, PlayerRef owner, String name);
    }

    private final AtomicReference<@Nullable GoTo> ref = new AtomicReference<>();

    /** Bind the player-warp go-to teleport; called once by the player-warps wiring on enable. */
    public void bind(GoTo goTo) {
        ref.set(goTo);
    }

    /** Send {@code viewer} to {@code owner}'s warp {@code name}, or no-op when player-warps is disabled. */
    public void teleport(PlayerRef viewer, PlayerRef owner, String name) {
        GoTo goTo = ref.get();
        if (goTo != null) {
            goTo.teleport(viewer, owner, name);
        }
    }
}
