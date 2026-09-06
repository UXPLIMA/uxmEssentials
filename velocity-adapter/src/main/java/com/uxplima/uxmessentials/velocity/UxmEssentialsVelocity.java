package com.uxplima.uxmessentials.velocity;

import java.nio.file.Path;
import java.util.Objects;

import com.google.inject.Inject;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.velocity.commandcontrol.ProxyCommandControl;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * The proxy-side cross-server bus broker, the entry point of the optional {@code uxmessentials-velocity}
 * jar. It registers the {@link BusChannel bus channel} on the proxy and relays uxmEssentials sync frames
 * from one backend to the others, so a {@code /sethome} on one backend invalidates the home cache on every
 * other backend in the cluster ({@code docs/09-deployment.md} Path B).
 *
 * <p>This class is a thin Velocity-lifecycle shell: it holds the injected {@link ProxyServer} and
 * {@link Logger}, registers the channel on proxy init, and forwards the plugin-message event to the
 * {@link BusBroker}, which owns the fan-out logic (and is plain Java, testable without a live proxy).
 * Construction is by constructor injection. The only fields are the two injected collaborators and the
 * broker built from them.
 */
@Plugin(
        id = "uxmessentials-velocity",
        name = "uxmEssentials Velocity",
        version = BuildConstants.VERSION,
        description = "Cross-server bus broker and proxy command-control for uxmEssentials.",
        authors = {"UXPLIMA"},
        dependencies = {@Dependency(id = "luckperms", optional = true)})
@NullMarked
public final class UxmEssentialsVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final BusBroker broker;
    private final MinecraftChannelIdentifier channel;

    @Inject
    public UxmEssentialsVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.broker = new BusBroker(proxy, logger);
        this.channel = MinecraftChannelIdentifier.create(BusChannel.NAMESPACE, BusChannel.NAME);
    }

    /** Register the bus channel and, when configured, the proxy command-control layer on proxy init. */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(channel);
        logger.info("uxmEssentials bus broker ready on channel {}", channel.getId());
        ProxyCommandControl.enable(this, proxy, logger, dataDirectory);
    }

    /** Relay an incoming bus frame from its origin backend to every other backend. */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channel.getId().equals(event.getIdentifier().getId())) {
            return;
        }
        broker.relay(event);
    }
}
