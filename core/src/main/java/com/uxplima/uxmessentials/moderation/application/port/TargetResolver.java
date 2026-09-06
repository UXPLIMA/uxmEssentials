package com.uxplima.uxmessentials.moderation.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Resolves a sanction target by name, online <em>or</em> offline. The kernel {@link
 * com.uxplima.uxmessentials.shared.application.port.PlayerLookup} only resolves online players by name; a
 * sanction must reach an offline (or never-joined) target too, an offline {@code /jail}, a {@code /banip
 * <player>} against a known-but-offline UUID, a {@code /warn} on a player who has logged off, so the
 * moderation context owns this wider resolution.
 *
 * <p>The adapter resolves an online player first, then falls back to the server's offline-player cache (the
 * has-played-before profile), mapping each to a {@link PlayerRef}. A name that has never been seen resolves
 * to empty, which a command maps to {@code UNKNOWN_TARGET}.
 */
public interface TargetResolver {

    /** The player with this exact name, online or known offline; empty when never seen. */
    Optional<PlayerRef> resolve(String name);
}
