package com.uxplima.uxmessentials.shared.network;

/**
 * The plugin-messaging channel the cross-server bus rides on. A single pure constant so the backend bus
 * client (the bukkit adapter, which registers it as an outgoing/incoming channel) and the proxy broker (the
 * velocity adapter, which registers it on the proxy and relays frames between backends) name the exact same
 * channel, changing it on one side without the other silences the bridge ({@code docs/09-deployment.md}
 * Path B). The default is {@code uxmessentials:bus_v1}; an operator may override the backend's side from
 * {@code network.conf > bus-channel}, but the proxy registers this canonical default.
 */
public final class BusChannel {

    /** The namespace segment of the channel key. */
    public static final String NAMESPACE = "uxmessentials";

    /** The name segment of the channel key; the {@code _v1} suffix is the wire-format generation. */
    public static final String NAME = "bus_v1";

    /** The full channel key {@code uxmessentials:bus_v1} as registered on both sides. */
    public static final String FULL = NAMESPACE + ":" + NAME;

    private BusChannel() {}
}
