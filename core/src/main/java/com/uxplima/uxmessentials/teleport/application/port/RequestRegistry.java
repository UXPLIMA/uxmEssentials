package com.uxplima.uxmessentials.teleport.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;

/**
 * Outbound port over the in-flight {@code tpa} requests. Whether a new request displaces a pending one
 * (single-request-displace mode) or stacks (multi-queue mode) is the operator's choice; this port
 * exposes both shapes. {@link #pendingFor(PlayerRef)} returns the queue a target may resolve, oldest
 * first. The registry is runtime state the teleport module clears on {@code stop()}; nothing is
 * persisted.
 */
public interface RequestRegistry {

    /** Store a freshly opened request, applying the configured displace-or-queue mode for the target. */
    void store(TeleportRequest request);

    /** Look up a request by id, for an accept/deny/cancel that names a specific one. */
    Optional<TeleportRequest> byId(RequestId id);

    /** The pending requests aimed at {@code target}, oldest first (one element in displace mode). */
    List<TeleportRequest> pendingFor(PlayerRef target);

    /** The pending request {@code requester} issued, if any (for {@code /tpcancel}). */
    Optional<TeleportRequest> outgoing(PlayerRef requester);

    /** Remove a resolved request so it can no longer be acted on. */
    void remove(RequestId id);
}
