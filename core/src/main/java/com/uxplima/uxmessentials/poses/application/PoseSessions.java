package com.uxplima.uxmessentials.poses.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The single source of truth for who is currently posing, a per-player registry of active {@link PoseSession}s.
 * The placeholder surface, the ghost-entity cleanup, and the one-session-per-player invariant all read and mutate
 * through this one object, so a pose is started, replaced, or ended in exactly one place.
 *
 * <p>Backed by a {@code ConcurrentHashMap<UUID, PoseSession>} mutated through {@code compute}/{@code remove}, the
 * per-player-keyed concurrency pattern the project mandates: a start replaces any prior session atomically (there
 * is never a moment with two sessions for one player), and a stop removes it. Keyed by the player's UUID so a
 * reconnect with a fresh {@link PlayerRef} name still resolves the same entry.
 *
 * <p>Empty and fully usable from Phase 0: the later behaviour phases hang {@code /sit}, the listeners, and the seat
 * entities off it without changing its shape. On module stop the registry is {@link #clear() cleared} so a reload
 * leaves zero residual runtime state.
 */
public final class PoseSessions {

    private final ConcurrentHashMap<UUID, PoseSession> sessions = new ConcurrentHashMap<>();

    /**
     * Record {@code session} as {@code subject}'s active pose, replacing any pose they already held (the
     * one-session-per-player invariant). Returns the session that was replaced, or empty when they were not posing.
     */
    public Optional<PoseSession> start(PoseSession session) {
        Objects.requireNonNull(session, "session");
        return Optional.ofNullable(sessions.put(session.subject().uuid(), session));
    }

    /** End {@code who}'s pose, returning the session that was removed, or empty when they were not posing. */
    public Optional<PoseSession> stop(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(sessions.remove(who.uuid()));
    }

    /** The player's active pose, or empty when they are not posing. */
    public Optional<PoseSession> current(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(sessions.get(who.uuid()));
    }

    /** Whether {@code who} is currently posing. */
    public boolean isPosing(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return sessions.containsKey(who.uuid());
    }

    /**
     * The players currently sitting <em>on</em> {@code target}. The subjects of the {@link
     * com.uxplima.uxmessentials.poses.domain.PoseType#PLAYER_SIT} sessions whose target is {@code target}. Used to
     * end each rider's pose when their carrier logs out or is removed, so no rider is left with a stuck session once
     * the carrier is gone. The returned list is a snapshot, safe to iterate while stopping each rider.
     */
    public List<PlayerRef> ridersOf(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return sessions.values().stream()
                .filter(session -> target.equals(session.target()))
                .map(PoseSession::subject)
                .toList();
    }

    /** How many players are posing right now. */
    public int size() {
        return sessions.size();
    }

    /** Drop every active session, called on module stop so a disable/reload leaves no residual state. */
    public void clear() {
        sessions.clear();
    }
}
