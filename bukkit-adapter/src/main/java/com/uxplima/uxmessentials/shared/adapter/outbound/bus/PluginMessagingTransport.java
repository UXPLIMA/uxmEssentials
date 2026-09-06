package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.jspecify.annotations.NullMarked;

/**
 * The plugin-messaging {@link BusTransport}: it moves opaque frame bytes over a carrier player's connection
 * through the proxy. Registers the plugin-messaging channel with Bukkit's {@code Messenger}, buffers outbound
 * frames and flushes them on any online player, and feeds every inbound frame's bytes back to the
 * {@code onFrame} sink the {@link BusCore} above the seam registers. It knows nothing about
 * {@code NetworkMessage} or the codec, the encode/decode, origin stamp, self-origin loop sentinel and listener
 * dispatch all live above this seam in {@link BusCore}; only the byte-moving machinery is here.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b> for the outbound buffer ({@link #outbound}, guarded by its own
 * monitor for the small bounded push/drain). Every Bukkit touch. Registering the channel, sending a frame
 * through a carrier player. Hops onto the right thread through the injected {@link Scheduler} port; the
 * inbound hand-off runs off the tick thread via {@link Scheduler#async}. The transport never blocks a region
 * thread and never calls a Bukkit API off it.
 *
 * <h2>Degradation</h2>
 * Plugin messages ride a player connection, so a frame can only leave once a player is online to carry it.
 * With no proxy, no peers, or no online players the buffered frames simply never drain and the bus is a no-op
 *, the plugin runs fully local. This is the "degrades to local-only when no proxy/peer responds" contract:
 * nothing about the single-server happy path depends on the transport ({@code docs/02-concurrency.md}).
 */
@NullMarked
public final class PluginMessagingTransport implements PluginMessageListener, BusTransport {

    private static final byte[] EMPTY = new byte[0];

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final String channel;
    private final int outboundQueueSize;
    private final Deque<byte[]> outbound = new ArrayDeque<>();

    private volatile boolean running;
    private @org.jspecify.annotations.Nullable Consumer<byte[]> onFrame;

    public PluginMessagingTransport(Plugin plugin, Scheduler scheduler, String channel, int outboundQueueSize) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.channel = Objects.requireNonNull(channel, "channel");
        if (outboundQueueSize < 1) {
            throw new IllegalArgumentException("outboundQueueSize must be positive: " + outboundQueueSize);
        }
        this.outboundQueueSize = outboundQueueSize;
    }

    /** Register the plugin-messaging channel and route every inbound frame's bytes to {@code onFrame}. */
    @Override
    public void start(Consumer<byte[]> onFrame) {
        this.onFrame = Objects.requireNonNull(onFrame, "onFrame");
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, this);
        running = true;
    }

    /** Unregister the channel and drop any buffered frames. Idempotent. */
    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, channel, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        synchronized (outbound) {
            outbound.clear();
        }
    }

    /**
     * Buffer one already-encoded frame and schedule a flush. Bounded: when the buffer is full the oldest frame
     * is dropped rather than pinning memory. A no-op when the transport is not running (stopped or never
     * started), so a publish before start never touches the scheduler.
     */
    @Override
    public void send(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (!running) {
            return;
        }
        synchronized (outbound) {
            while (outbound.size() >= outboundQueueSize) {
                outbound.pollFirst();
            }
            outbound.addLast(frame);
        }
        scheduler.onGlobal(this::flush);
    }

    /** True when the channel is registered and a frame can be carried as soon as a player is online. */
    @Override
    public boolean healthy() {
        return running;
    }

    @Override
    public void onPluginMessageReceived(String inboundChannel, Player carrier, byte[] frame) {
        Consumer<byte[]> sink = onFrame;
        if (!running || sink == null || !channel.equals(inboundChannel)) {
            return;
        }
        // Hand the bytes off the tick thread: this handler may run on the carrier's region thread and the core's
        // decode + dispatch does cache work, not Bukkit work.
        byte[] copy = frame.clone();
        scheduler.async(() -> sink.accept(copy));
    }

    private void flush() {
        Player carrier = anyCarrier();
        if (carrier == null) {
            return;
        }
        // Drain the buffer through one carrier. The proxy routes by channel, not by the carrier identity, so any
        // online player can carry a server-wide frame.
        for (byte[] frame = drainOne(); frame.length > 0; frame = drainOne()) {
            carrier.sendPluginMessage(plugin, channel, frame);
        }
    }

    private byte[] drainOne() {
        synchronized (outbound) {
            byte[] frame = outbound.pollFirst();
            // An empty array is the "nothing left" sentinel: a real frame always carries the version byte.
            return frame == null ? EMPTY : frame;
        }
    }

    private @org.jspecify.annotations.Nullable Player anyCarrier() {
        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        return online.isEmpty() ? null : online.iterator().next();
    }
}
