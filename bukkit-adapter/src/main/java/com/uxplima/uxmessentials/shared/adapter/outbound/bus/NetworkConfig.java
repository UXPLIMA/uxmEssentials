package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import org.jspecify.annotations.NullMarked;

/**
 * The backend's {@code network} view: whether this backend opts into cross-server sync, its unique
 * {@code server-id} (the origin tag stamped into every outbound frame and the loop sentinel on inbound), the
 * bus channel name, the bounded outbound queue size ({@code docs/09-deployment.md} Path B), and, added in
 * Phase B, which {@link Transport} carries the bus and the canonical {@link Redis} connection block the Redis
 * transport uses. These are restart-only. The channel, the captured server-id and the chosen transport are
 * bound once at enable, so a single immutable snapshot is read at wiring time.
 *
 * @param enabled whether this backend participates in network sync; {@code false} runs purely local
 * @param serverId this backend's unique id; two backends sharing it corrupt origin routing
 * @param channel the plugin-messaging channel the proxy broker registers
 * @param outboundQueueSize the cap on buffered outbound frames before the oldest are dropped
 * @param heartbeatSeconds how often this backend announces its presence with a {@code ServerPing}; a peer
 *     ages out of the {@code /uxmess doctor} count after a few missed beats
 * @param transport which carrier(s) move the bus: the proxy plugin-messaging path, Redis pub/sub, or both
 * @param transportRecognized whether {@code transport} parsed cleanly; {@code false} means it fell back to
 *     {@code velocity} from an unknown value, so the wiring can WARN without crashing enable
 * @param redis the canonical Redis connection block the Redis transport consumes
 */
@NullMarked
public record NetworkConfig(
        boolean enabled,
        String serverId,
        String channel,
        int outboundQueueSize,
        int heartbeatSeconds,
        Transport transport,
        boolean transportRecognized,
        Redis redis) {

    private static final int DEFAULT_QUEUE = 256;
    private static final int DEFAULT_HEARTBEAT_SECONDS = 30;
    private static final String DEFAULT_SERVER_ID = "server-1";

    public NetworkConfig {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(redis, "redis");
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("server-id must not be blank");
        }
        if (outboundQueueSize < 1) {
            throw new IllegalArgumentException("outbound-queue-size must be positive: " + outboundQueueSize);
        }
        if (heartbeatSeconds < 1) {
            throw new IllegalArgumentException("heartbeat-seconds must be positive: " + heartbeatSeconds);
        }
    }

    /** The heartbeat publish interval the {@code ClusterHeartbeat} schedules on. */
    public java.time.Duration heartbeatInterval() {
        return java.time.Duration.ofSeconds(heartbeatSeconds);
    }

    /**
     * How long a peer stays "live" after its last heartbeat: three beats, so a peer that misses one or two
     * pings does not flap out of the cluster-peer count.
     */
    public java.time.Duration peerLivenessWindow() {
        return heartbeatInterval().multipliedBy(3);
    }

    /** The carrier(s) the bus rides. {@link #VELOCITY} is the proxy default: no Redis connection is opened. */
    public enum Transport {
        VELOCITY,
        REDIS,
        BOTH;

        /** Parse a config value, trimming and case-folding; an unknown value yields an empty optional. */
        static java.util.Optional<Transport> parse(String raw) {
            Objects.requireNonNull(raw, "raw");
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            for (Transport value : values()) {
                if (value.name().equals(normalized)) {
                    return java.util.Optional.of(value);
                }
            }
            return java.util.Optional.empty();
        }
    }

    /**
     * The canonical Redis connection block, read from the {@code network.redis} subtree. These are exactly the
     * values the Redis transport's factory consumes (the {@code RedisTransportFactory} the {@code :redis-adapter}
     * companion publishes through the {@code ServicesManager}); the factory appends {@code db} to the connection
     * URI ({@code 0} leaves the default database). Redis pub/sub is global across logical databases, so {@code db}
     * has no functional
     * effect on this transport. It is carried only for back-compat with existing {@code network.redis.db}
     * settings.
     *
     * @param host the Redis host
     * @param port the Redis port
     * @param password the Redis auth password, or empty to skip auth
     * @param channel the Redis pub/sub channel both sides publish to and subscribe on
     * @param db the Redis logical database index appended to the connection URI for config back-compat
     */
    public record Redis(String host, int port, String password, String channel, int db) {

        private static final String DEFAULT_HOST = "127.0.0.1";
        private static final int DEFAULT_PORT = 6379;
        private static final String DEFAULT_CHANNEL = "uxmessentials:bus";

        public Redis {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(channel, "channel");
        }

        static Redis from(ConfigStore config) {
            return new Redis(
                    config.getString("network.redis.host", DEFAULT_HOST),
                    config.getInt("network.redis.port", DEFAULT_PORT),
                    config.getString("network.redis.password", ""),
                    config.getString("network.redis.channel", DEFAULT_CHANNEL),
                    config.getInt("network.redis.db", 0));
        }
    }

    /**
     * Read the network settings from the {@code network} subtree of {@code config}. The channel defaults to the
     * canonical {@link BusChannel#FULL}; an operator overriding it must match the proxy, or the bridge silences
     * ({@code docs/09-deployment.md}). The bus is disabled by default so a single-server install runs with no
     * proxy and no behavioural change, and {@code transport} defaults to {@code velocity} so even an enabled
     * backend opens no Redis connection unless asked.
     */
    public static NetworkConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        boolean enabled = config.getBoolean("network.enabled", false);
        String serverId = config.getString("network.server-id", DEFAULT_SERVER_ID);
        String channel = config.getString("network.bus-channel", BusChannel.FULL);
        int queue = config.getInt("network.bus.outbound-queue-size", DEFAULT_QUEUE);
        // Floor at one second so a fat-fingered zero/negative cannot trip the constructor's positivity check.
        int heartbeat = Math.max(1, config.getInt("network.heartbeat-seconds", DEFAULT_HEARTBEAT_SECONDS));
        String rawTransport = config.getString("network.transport", Transport.VELOCITY.name());
        java.util.Optional<Transport> parsed = Transport.parse(rawTransport);
        return new NetworkConfig(
                enabled,
                serverId.isBlank() ? DEFAULT_SERVER_ID : serverId,
                channel,
                queue,
                heartbeat,
                parsed.orElse(Transport.VELOCITY),
                parsed.isPresent(),
                Redis.from(config));
    }
}
