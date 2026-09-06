package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * Reads a player's primary permission group for the command gate, keeping a permission plugin a soft dependency
 * exactly as the chat renderer's {@code ChatMetaSource} does: the default {@link #empty()} source reports no group,
 * so a server without LuckPerms gates every player through the {@code default} command list, and
 * {@link LuckPermsPlayerGroupSource} binds only when LuckPerms is installed (probed in {@link PlayerGroupSources}).
 *
 * <p>The read happens on the tick thread inside the preprocess gate, so an implementation must only read already-cached
 * data, never block or load a user, which the LuckPerms-backed source does (it reads the loaded user's cached meta).
 */
@NullMarked
public interface PlayerGroupSource {

    /** The primary permission group of {@code who}, or {@link Optional#empty()} when none is exposed. */
    Optional<String> groupOf(UUID who);

    /** A source that never reports a group: the default when no permission plugin exposes one. */
    static PlayerGroupSource empty() {
        return who -> Optional.empty();
    }
}
