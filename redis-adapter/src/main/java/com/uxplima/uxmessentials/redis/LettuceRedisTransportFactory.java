package com.uxplima.uxmessentials.redis;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.RedisTransportFactory;

/**
 * The companion's {@link RedisTransportFactory}. The implementation published to the host through the
 * {@code ServicesManager}. It is a thin delegate to {@link RedisBusTransports#redis}, the existing static
 * builder the tests already exercise; this type only exists to give the factory a published, host-resolvable
 * service instance.
 *
 * <p>The returned {@link BusTransport} is the host's own {@code BusTransport} class (resolved through the joined
 * classpath this companion declares in {@code paper-plugin.yml}), so the host's {@code BusCore} accepts it with
 * no loader-constraint {@link LinkageError}. Lettuce stays bundled and relocated in this jar alone, the host
 * never names a Redis symbol.
 */
final class LettuceRedisTransportFactory implements RedisTransportFactory {

    @Override
    public BusTransport redis(
            String host, int port, String password, int db, String channel, Scheduler scheduler, Logger log) {
        return RedisBusTransports.redis(host, port, password, db, channel, scheduler, log);
    }
}
