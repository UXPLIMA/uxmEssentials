package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.RedisTransportFactory;
import org.jspecify.annotations.NullMarked;

/**
 * The Redis arm of the bus, resolved lazily on {@link #start}. The Redis transport lives in the optional
 * {@code uxmEssentials-redis} companion plugin, which registers a {@link RedisTransportFactory} with the
 * {@code ServicesManager} on <em>its</em> enable. Because the companion is declared to load after the host, the
 * host cannot resolve the factory during its own enable, so the host defers starting the bus to its first
 * global tick and this transport resolves the factory at that point, by which time every plugin has enabled.
 *
 * <p>On {@link #start} it looks the factory up: present (companion deployed) → build the real Redis transport
 * and delegate to it; absent → degrade to a {@link LocalOnlyBusTransport} and log one WARN telling the operator
 * to drop the companion jar in {@code plugins/}. There is no class-reference {@code LinkageError} to guard, the
 * host never names a Lettuce or companion type directly; the lookup is a plain null check across the
 * {@code ServicesManager} seam, and the factory returns the host's own {@link BusTransport} class (the companion
 * joins the host classpath), so no loader-constraint violation can arise.
 *
 * <h2>Concurrency</h2>
 * Ownership: {@code delegate} is written once by {@link #start} (the deferred first-tick task) before any
 * {@link #send} can run, and is read by {@link #send}, {@link #stop} and {@link #healthy}. It is {@code volatile}
 * so a frame published from another thread after start sees the resolved delegate; before start it is the
 * inert local-only default, so a stray early send is a safe no-op.
 */
@NullMarked
final class DeferredRedisTransport implements BusTransport {

    private final Plugin plugin;
    private final NetworkConfig.Redis redis;
    private final Scheduler scheduler;
    private final Logger log;

    private volatile BusTransport delegate = new LocalOnlyBusTransport();

    DeferredRedisTransport(Plugin plugin, NetworkConfig.Redis redis, Scheduler scheduler, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void start(Consumer<byte[]> onFrame) {
        Objects.requireNonNull(onFrame, "onFrame");
        BusTransport resolved = resolve();
        delegate = resolved;
        resolved.start(onFrame);
    }

    private BusTransport resolve() {
        RedisTransportFactory factory = plugin.getServer().getServicesManager().load(RedisTransportFactory.class);
        if (factory == null) {
            log.warn("network.transport=redis but the uxmEssentials-redis companion plugin is not deployed; "
                    + "redis bus disabled. Drop uxmEssentials-redis.jar in plugins/ to enable the redis transport");
            return new LocalOnlyBusTransport();
        }
        return factory.redis(redis.host(), redis.port(), redis.password(), redis.db(), redis.channel(), scheduler, log);
    }

    @Override
    public void send(byte[] frame) {
        delegate.send(frame);
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public boolean healthy() {
        return delegate.healthy();
    }

    /** Test seam: the delegate currently backing this transport (the local-only default before start). */
    BusTransport delegate() {
        return delegate;
    }
}
