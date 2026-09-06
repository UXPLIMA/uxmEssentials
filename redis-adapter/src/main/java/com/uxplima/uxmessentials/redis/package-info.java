/**
 * The cross-server bus's Redis transport, shipped as the standalone {@code uxmEssentials-redis} companion Paper
 * plugin (docs/09-deployment.md). {@link com.uxplima.uxmessentials.redis.RedisBusTransportAdapter} implements
 * the host's {@code BusTransport} SPI on top of uxmlib's Lettuce-backed {@code RedisBus}, carrying the full
 * {@code NetworkMessageCodec} frame bytes between backends over a single Redis pub/sub channel with no Velocity
 * proxy in the path; the {@link com.uxplima.uxmessentials.redis.RedisBusTransports} factory builds it from
 * configuration so the host references only pure-Java port types and never names a Lettuce symbol.
 *
 * <p>This module is a real Paper plugin, not a library jar: its
 * {@link com.uxplima.uxmessentials.redis.UxmEssentialsRedisPlugin} publishes a
 * {@link com.uxplima.uxmessentials.redis.LettuceRedisTransportFactory} through the {@code ServicesManager}, and
 * the host looks it up to build the transport. The plugin joins the host's classpath (paper-plugin.yml) so its
 * {@code BusTransport} is the host's own class. The only way the transport instance can cross into the host's
 * bus core without a loader-constraint {@code LinkageError}. Consequently {@code :core} and {@code paper-api}
 * are compile-only and never shaded here.
 *
 * <p>Lettuce (and its Netty/Reactor transitives) is bundled and relocated into this jar alone, the main
 * {@code uxmEssentials} jar carries no Redis client at all. An operator who wants the Redis transport drops
 * this companion jar in {@code plugins/}; when it is absent the host degrades the bus to local-only.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.redis;
