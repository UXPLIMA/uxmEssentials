package com.uxplima.uxmessentials.security.adapter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The transient record of when each player last proved their second factor, the input the op-command re-auth window
 * is measured against. It is stamped from two places, a successful join verification and a successful re-auth prompt
 *, so a player who has just verified is not immediately asked to re-verify to run a protected command. The stamp is
 * a plain {@link Instant}; the {@link com.uxplima.uxmessentials.security.domain.ReauthPolicy} decides whether it is
 * still recent enough.
 *
 * <p>Purely in-memory and owned by the security adapter: a stamp deliberately does not survive a restart or a relog
 * (a rejoin re-runs the join freeze, which re-stamps on success), so there is no persistence and no leak beyond the
 * session. The map is a {@link ConcurrentHashMap} because the join and re-auth verify workers run off the tick thread;
 * every mutation is a single atomic map operation. Everything is dropped on {@link #clearAll()} at module stop.
 */
@NullMarked
public final class ReauthState {

    /** UUID → the instant that player last proved a factor; absent when they have not verified this session. */
    private final ConcurrentHashMap<UUID, Instant> lastVerified = new ConcurrentHashMap<>();

    /** Record that {@code playerId} proved a factor at {@code when}, opening a fresh re-auth window. */
    public void stamp(UUID playerId, Instant when) {
        lastVerified.put(Objects.requireNonNull(playerId, "playerId"), Objects.requireNonNull(when, "when"));
    }

    /** The instant {@code playerId} last verified, or {@code null} when they have not verified this session. */
    public @Nullable Instant lastVerified(UUID playerId) {
        return lastVerified.get(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Forget {@code playerId}'s verification: on disconnect, so a rejoin must verify again. */
    public void clear(UUID playerId) {
        lastVerified.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Drop every stamp, called on module stop so no verification window survives a disable. */
    public void clearAll() {
        lastVerified.clear();
    }
}
