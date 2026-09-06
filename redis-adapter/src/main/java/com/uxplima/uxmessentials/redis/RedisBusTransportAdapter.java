package com.uxplima.uxmessentials.redis;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmlib.redis.RedisBus;

/**
 * The Redis pub/sub {@link BusTransport}: it carries the whole cross-server bus over a single Redis channel with
 * no Velocity proxy in the path. Where {@code PluginMessagingTransport} rides a carrier player's connection
 * through the proxy, this transport publishes each already-encoded {@code NetworkMessageCodec} frame to a Redis
 * channel and subscribes to that same channel, so every {@code NetworkMessage} type and every {@code *Sync}
 * context replicates straight between backends sharing a Redis server.
 *
 * <p>This is a thin adapter over uxmlib's {@link RedisBus}, whose SPI takes the channel as a per-call argument
 * and has no {@code start}/{@code healthy} of its own. The byte-moving, Lettuce PUBLISH / SUBSCRIBE, binary
 * codec, auto-reconnect, the Netty event loop. Lives in {@link RedisBus}; this adapter pins the fixed channel,
 * adds the {@link BusTransport} lifecycle, and re-applies the fail-degraded publish guard at the synchronous
 * seam (the lib only catches an asynchronous publish future's failure, not a synchronous throw from the publish
 * call itself).
 *
 * <p>Frames are carried as opaque binary: the transport knows nothing about {@code NetworkMessage} or the codec
 * the encode/decode, origin stamp, self-origin loop sentinel and listener dispatch all live above this seam in
 * {@code BusCore}, which also bridges inbound frames back onto a tick thread through the {@link Scheduler} port.
 * This transport adds no second de-duplication mechanism, and Lettuce already runs its I/O off any tick thread,
 * so the adapter never needs a scheduler hop of its own.
 *
 * <p>Publish failures log at most one WARN per 60s so a Redis outage cannot flood the log; the lib throttles the
 * asynchronous path the same way, and this adapter throttles the synchronous one.
 *
 * <h2>Concurrency</h2>
 * Ownership: the {@code running} flag is <b>volatile</b>, written by {@link #start}/{@link #stop} and read by
 * {@link #send} and {@link #healthy}. {@link RedisBus} owns its own threading, Lettuce runs all I/O on its event
 * loop, so neither subscribe nor publish ever blocks a tick or region thread.
 */
public final class RedisBusTransportAdapter implements BusTransport {

    private static final long PUBLISH_WARN_INTERVAL_MS = 60_000L;

    private final RedisBus channel;
    private final String channelName;
    private final Logger log;

    private final AtomicLong lastPublishWarnAt = new AtomicLong(0L);

    private volatile boolean running;

    public RedisBusTransportAdapter(RedisBus channel, String channelName, Logger log) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.channelName = Objects.requireNonNull(channelName, "channelName");
        this.log = Objects.requireNonNull(log, "log");
        if (channelName.isBlank()) {
            throw new IllegalArgumentException("channelName must not be blank");
        }
    }

    /**
     * Arm the subscription on the configured channel, routing every received frame's bytes to {@code onFrame}.
     * Subscribe runs on Lettuce's event loop, so this never blocks a tick thread. No-op if already running.
     */
    @Override
    public void start(Consumer<byte[]> onFrame) {
        Objects.requireNonNull(onFrame, "onFrame");
        if (running) {
            return;
        }
        running = true;
        channel.subscribe(channelName, onFrame);
    }

    /**
     * Publish one already-encoded frame to the Redis channel. Fire-and-forget and a no-op when the transport is
     * not running, so a send before start never touches Redis. The frame is defensively copied so the caller's
     * array cannot mutate under the publish, and the publish is wrapped in a synchronous fail-degraded guard
     * with a rate-limited WARN, mirroring the lib's asynchronous guard.
     */
    @Override
    public void send(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (!running) {
            return;
        }
        try {
            channel.publish(channelName, frame.clone());
        } catch (RuntimeException publishFailed) {
            warnRateLimited(publishFailed);
        }
    }

    /** Close the channel and stop delivering frames. Idempotent; safe to call when start was a no-op. */
    @Override
    public void stop() {
        running = false;
        channel.close();
    }

    /** Delegates to the live wire state of the underlying {@link RedisBus}; false once stopped. */
    @Override
    public boolean healthy() {
        return running && channel.healthy();
    }

    private void warnRateLimited(RuntimeException cause) {
        long now = System.currentTimeMillis();
        long previous = lastPublishWarnAt.get();
        if (now - previous >= PUBLISH_WARN_INTERVAL_MS && lastPublishWarnAt.compareAndSet(previous, now)) {
            log.warn("redis bus transport failed to publish a frame to {}: {}", channelName, cause);
        }
    }
}
