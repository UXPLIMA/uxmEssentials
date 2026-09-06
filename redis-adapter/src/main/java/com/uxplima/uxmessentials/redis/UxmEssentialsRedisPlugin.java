package com.uxplima.uxmessentials.redis;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.shared.network.RedisTransportFactory;
import org.jspecify.annotations.NullMarked;

/**
 * The entry point of the optional {@code uxmEssentials-redis} jar. Its own Paper plugin, declared by this
 * module's {@code paper-plugin.yml}. It exists for one reason: to publish a {@link RedisTransportFactory} the
 * host {@code uxmEssentials} can look up through Bukkit's {@code ServicesManager} and build the cross-server
 * bus's Redis transport from.
 *
 * <h2>Why a plugin and not a library</h2>
 * The Redis transport implements the host jar's {@code BusTransport}, and that instance is handed to the host's
 * bus core. For the JVM to accept the hand-off both sides must resolve {@code BusTransport} to the same class
 * object. A library jar on the host's classloader (or one shading its own {@code core} copy) cannot satisfy
 * that: it yields a loader-constraint {@code LinkageError}. So this is a real plugin that joins the host's
 * classpath ({@code paper-plugin.yml > dependencies > server > uxmEssentials > join-classpath: true}), giving
 * it the host's own {@code BusTransport} and {@link RedisTransportFactory} types. Lettuce (and its Netty/Reactor
 * transitives) is bundled and relocated in this jar alone; the host carries no Redis client.
 *
 * <h2>Timing</h2>
 * This companion is declared to load <em>after</em> the host, so the host has already wired its bus by the time
 * this enables. The host defers resolving the Redis transport to its first global tick: past every plugin's
 * enable, so the factory registered here is in place when the host looks it up. Registering the factory does
 * not classload Lettuce: {@link LettuceRedisTransportFactory} touches no Redis symbol until its {@code redis}
 * method is actually called.
 */
@NullMarked
public final class UxmEssentialsRedisPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer()
                .getServicesManager()
                .register(
                        RedisTransportFactory.class, new LettuceRedisTransportFactory(), this, ServicePriority.Normal);
        getLogger().info("redis transport factory registered; uxmEssentials will use it when network.transport=redis");
    }

    @Override
    public void onDisable() {
        // Drop our service so a host reload after this plugin unloads cannot resolve a dead factory.
        getServer().getServicesManager().unregisterAll(this);
    }
}
