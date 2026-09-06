package com.uxplima.uxmessentials.discord;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import com.uxplima.uxmessentials.api.link.DiscordBridgePresence;

/**
 * Advertises this bridge's presence to the host plugin through Bukkit's {@code ServicesManager}. The host reads
 * the registration to decide whether issuing a {@code /discordlink} code is worthwhile: a code only ever matters
 * if a connected bridge can redeem it, so the bridge registers exactly one {@link DiscordBridgePresence} once it
 * is connected and the host's confirmation seam is wired, and unregisters it on shutdown.
 *
 * <p>The shared {@link DiscordBridgePresence} type lives in {@code :api} (the only module both jars depend on),
 * so the host sees this registration by the same class identity across the joined classpath: there is no
 * compile-time link between the two jars, the same pattern the confirmation seam already uses. The live marker
 * is held in an {@link AtomicReference} so a disable racing the async connect either unregisters the whole
 * registration or finds none, and a double {@link #publish} or {@link #withdraw} stays idempotent.
 */
final class BridgePresence {

    private final ServicesManager services;
    private final Plugin plugin;
    private final AtomicReference<DiscordBridgePresence> registered = new AtomicReference<>();

    BridgePresence(ServicesManager services, Plugin plugin) {
        this.services = Objects.requireNonNull(services, "services");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Register a presence marker so the host treats a link code as redeemable. Idempotent. */
    void publish() {
        DiscordBridgePresence marker = new DiscordBridgePresence() {};
        if (!registered.compareAndSet(null, marker)) {
            return;
        }
        services.register(DiscordBridgePresence.class, marker, plugin, ServicePriority.Normal);
    }

    /** Withdraw the presence marker so the host stops issuing link codes. Idempotent. */
    void withdraw() {
        DiscordBridgePresence marker = registered.getAndSet(null);
        if (marker != null) {
            services.unregister(marker);
        }
    }
}
