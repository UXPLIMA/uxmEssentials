package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * A server-presence heartbeat. Each enabled backend's {@code ClusterHeartbeat} publishes one on a bounded
 * interval ({@code network.heartbeat-seconds}); a peer receiving it records the origin as a live cluster
 * member with a fresh last-seen timestamp in its {@code ClusterPeers} roster. That roster backs the
 * cluster-peer count and last-activity line in {@code /uxmess doctor} ({@code docs/09-deployment.md}), a peer
 * count of zero on a multi-backend cluster means the proxy broker is missing or the bus channel names diverged.
 *
 * @param originServer the backend announcing its presence
 * @param epochMillis the origin's wall-clock send time, for last-seen bookkeeping and skew diagnostics
 */
public record ServerPing(String originServer, long epochMillis) implements NetworkMessage {

    public ServerPing {
        Objects.requireNonNull(originServer, "originServer");
    }

    @Override
    public MessageType type() {
        return MessageType.SERVER_PING;
    }
}
