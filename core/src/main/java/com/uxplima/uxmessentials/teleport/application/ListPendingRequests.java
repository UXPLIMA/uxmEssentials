package com.uxplima.uxmessentials.teleport.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;

/**
 * Reports the {@code tpa} requests a player has waiting on them, backs {@code /tpalist}. Reads the
 * registry's pending queue (oldest first) and either tells the viewer nothing is pending or sends a
 * header followed by one entry per requester, so a player can see who to {@code /tpaccept} or
 * {@code /tpdeny}. Read-only: it never resolves or expires a request.
 */
public final class ListPendingRequests {

    private final RequestRegistry requests;
    private final Notifier notifier;

    public ListPendingRequests(RequestRegistry requests, Notifier notifier) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Show {@code viewer} the requests aimed at them, oldest first. */
    public void list(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        List<TeleportRequest> pending = requests.pendingFor(viewer);
        if (pending.isEmpty()) {
            notifier.send(viewer, TeleportMessageKey.TPA_NONE_PENDING);
            return;
        }
        notifier.send(viewer, TeleportMessageKey.TPA_LIST_HEADER);
        for (TeleportRequest request : pending) {
            notifier.send(
                    viewer,
                    TeleportMessageKey.TPA_LIST_ENTRY,
                    Map.of("player", request.requester().name()));
        }
    }
}
