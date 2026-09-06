package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A late-bound handle to the player-warps repository the warp editor reads when it edits a player warp. The
 * warps module is wired before player-warps, so the editor cannot receive the repository in its constructor
 * instead it receives this handle (always non-null, constructor-injected and {@code final}), and the
 * player-warps wiring binds the repository into it once that context is wired. When player-warps is disabled
 * the handle stays empty and {@link #get()} returns {@code null}, so the editor simply shows no player-warp
 * data.
 *
 * <p>Ownership: the reference is written exactly once by the player-warps wiring on enable and read on the
 * editor's entity thread; the {@link AtomicReference} makes that publication safe across threads.
 */
@NullMarked
public final class PlayerWarpRepositoryHandle {

    private final AtomicReference<@Nullable PlayerWarpRepository> ref = new AtomicReference<>();

    /** Bind the player-warps repository; called once by the player-warps wiring on enable. */
    public void bind(PlayerWarpRepository repository) {
        ref.set(repository);
    }

    /** The bound repository, or {@code null} when player-warps is disabled or not yet wired. */
    public @Nullable PlayerWarpRepository get() {
        return ref.get();
    }
}
