package com.uxplima.uxmessentials.trade.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The in-memory book of pending {@code /trade} requests, keyed by target so {@code /trade accept|deny [player]} can find
 * a request the invoking player was sent. Each entry carries its own send time; a request older than the {@code ttl} is
 * treated as expired. {@link #resolve} removes and reports it as {@link Status#EXPIRED} rather than opening a window, so
 * a target who ignores a request for a minute finds it gone. The book keeps at most one live request per
 * (requester, target) pair: a re-send overwrites the previous entry.
 *
 * <p>The decision logic is pure (it reads only its own maps and the injected {@link Clock}) so the expiry and the
 * accept/deny resolution are unit-testable with a controllable clock. The maps are concurrent because command threads on
 * Folia touch the book from different regions.
 */
@NullMarked
public final class TradeRequests {

    /** How a {@code /trade accept|deny} lookup resolved against the book. */
    public enum Status {
        /** A live request was found and removed, open (accept) or acknowledge (deny) it. */
        MATCHED,
        /** A request was found but had already expired; it was removed and no trade should open. */
        EXPIRED,
        /** No request from the named (or any) requester was on the book. */
        NONE
    }

    /** The outcome of a {@link #resolve} lookup: the status and, when found, the matched request. */
    public record Match(Status status, @Nullable PendingTradeRequest request) {

        public Match {
            Objects.requireNonNull(status, "status");
        }

        static Match none() {
            return new Match(Status.NONE, null);
        }

        static Match of(Status status, PendingTradeRequest request) {
            return new Match(status, Objects.requireNonNull(request, "request"));
        }
    }

    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, PendingTradeRequest>> byTarget =
            new ConcurrentHashMap<>();

    public TradeRequests(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("request ttl must be positive: " + ttl);
        }
    }

    /** Record a fresh request, overwriting any prior request from the same requester to the same target. */
    public PendingTradeRequest submit(PlayerRef requester, PlayerRef target) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        PendingTradeRequest request = new PendingTradeRequest(requester, target, clock.instant());
        byTarget.computeIfAbsent(target.uuid(), key -> new ConcurrentHashMap<>())
                .put(requester.uuid(), request);
        return request;
    }

    /**
     * Find and remove the request {@code target} should accept or deny: the one from {@code requesterName} when a name
     * is given, otherwise the most recent. A found-but-expired request is removed and reported {@link Status#EXPIRED}.
     */
    public Match resolve(UUID target, @Nullable String requesterName) {
        Objects.requireNonNull(target, "target");
        ConcurrentHashMap<UUID, PendingTradeRequest> pending = byTarget.get(target);
        if (pending == null) {
            return Match.none();
        }
        PendingTradeRequest chosen = choose(pending, requesterName);
        if (chosen == null) {
            return Match.none();
        }
        pending.remove(chosen.requester().uuid());
        if (pending.isEmpty()) {
            byTarget.remove(target, pending);
        }
        return Match.of(expired(chosen) ? Status.EXPIRED : Status.MATCHED, chosen);
    }

    /** Whether {@code requester} has a live (unexpired) request outstanding to {@code target}. */
    public boolean hasPending(UUID requester, UUID target) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        ConcurrentHashMap<UUID, PendingTradeRequest> pending = byTarget.get(target);
        if (pending == null) {
            return false;
        }
        PendingTradeRequest request = pending.get(requester);
        return request != null && !expired(request);
    }

    /** The names of everyone with a live request outstanding to {@code target}, the accept/deny suggestion list. */
    public List<String> pendingRequesterNames(UUID target) {
        Objects.requireNonNull(target, "target");
        ConcurrentHashMap<UUID, PendingTradeRequest> pending = byTarget.get(target);
        if (pending == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (PendingTradeRequest request : pending.values()) {
            if (!expired(request)) {
                names.add(request.requester().name());
            }
        }
        return names;
    }

    /** Drop every request a departing player sent or was sent, so a disconnect strands nothing on the book. */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        byTarget.remove(player);
        for (Map<UUID, PendingTradeRequest> pending : byTarget.values()) {
            pending.remove(player);
        }
    }

    private @Nullable PendingTradeRequest choose(
            Map<UUID, PendingTradeRequest> pending, @Nullable String requesterName) {
        if (requesterName != null) {
            for (PendingTradeRequest request : pending.values()) {
                if (request.requester().name().equalsIgnoreCase(requesterName)) {
                    return request;
                }
            }
            return null;
        }
        PendingTradeRequest newest = null;
        for (PendingTradeRequest request : pending.values()) {
            if (newest == null || request.createdAt().isAfter(newest.createdAt())) {
                newest = request;
            }
        }
        return newest;
    }

    private boolean expired(PendingTradeRequest request) {
        return Duration.between(request.createdAt(), clock.instant()).compareTo(ttl) > 0;
    }
}
