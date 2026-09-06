package com.uxplima.uxmessentials.shared.network;

import java.util.function.Consumer;

/**
 * The pluggable way the cross-server bus moves opaque frame bytes between backends. A transport knows nothing
 * about {@link NetworkMessage} or the codec. It carries already-encoded {@code byte[]} frames and hands back
 * the raw bytes of every frame it receives. The transport-agnostic half of the bus (frame encode/decode,
 * origin stamping, the self-origin loop sentinel, listener dispatch) lives above this seam in {@code BusCore};
 * the "how bytes are moved" half lives behind it.
 *
 * <p>Pure Java by design (no Bukkit, Velocity, or codec dependency) so {@code core} can name the SPI while a
 * concrete transport (plugin-messaging over a carrier player, or Redis pub/sub) supplies its own machinery in
 * an adapter module. A backend wires exactly one transport.
 *
 * <h2>Degradation</h2>
 * A transport that currently has no path to peers (no proxy, no online carrier, a dropped Redis connection)
 * simply never delivers frames; {@link #send} is fire-and-forget and may discard a frame it cannot move. The
 * bus degrades to local-only with no behavioural change to the single-server path, so nothing about the happy
 * path depends on a transport being healthy.
 */
public interface BusTransport {

    /** Start the transport; deliver each received frame's bytes to onFrame. */
    void start(Consumer<byte[]> onFrame);

    /** Stop and release resources. Idempotent. */
    void stop();

    /** Send one already-encoded frame to all peers (fire-and-forget). */
    void send(byte[] frame);

    /** True when the transport is currently able to deliver frames. */
    boolean healthy();
}
