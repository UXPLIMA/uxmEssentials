package com.uxplima.uxmessentials.discord;

import java.util.Objects;
import java.util.Optional;

/**
 * Decides whether a {@link Notification} is forwarded and, if so, to which Discord channel, then hands the
 * line to the {@link DiscordGateway}. This is the testable heart of the bridge: it holds the gateway and the
 * live {@link DiscordConfig} and contains the filtering rules, with no JDA or Bukkit types in sight, so it is
 * exercised against a fake gateway with no live connection.
 *
 * <p>Rules, in order:
 * <ul>
 *   <li>nothing is forwarded unless the gateway is connected;
 *   <li>a category with no channel mapped in {@code discord.conf} is dropped;
 *   <li>an {@link EventCategory#ECONOMY} notification below {@code min-eco-notify} is dropped;
 *   <li>otherwise the message is posted to the mapped channel asynchronously.
 * </ul>
 *
 * <p>The {@code originServer = "discord"} sentinel is honoured upstream by the host plugin's bus: anything the
 * bridge posts originates here and is never re-published onto the cluster bus, so a Discord-mirrored event
 * cannot loop back and be mirrored twice (docs/09-deployment.md Path C). The forwarder only reads host
 * events; it never writes back to the bus, which is what keeps that sentinel invariant intact on this side.
 */
public final class NotificationForwarder {

    private final DiscordGateway gateway;
    private final DiscordConfig config;

    public NotificationForwarder(DiscordGateway gateway, DiscordConfig config) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Forward the notification if it passes every filter. Returns {@code true} when the message was handed to
     * the gateway, {@code false} when it was dropped: the boolean makes the decision unit-assertable.
     */
    public boolean forward(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        if (!gateway.isConnected() || belowEcoThreshold(notification)) {
            return false;
        }
        Optional<String> channelId = config.channelFor(notification.category());
        if (channelId.isEmpty()) {
            return false;
        }
        gateway.sendToChannel(channelId.get(), notification.message());
        return true;
    }

    private boolean belowEcoThreshold(Notification notification) {
        if (notification.category() != EventCategory.ECONOMY) {
            return false;
        }
        return notification
                .amount()
                .map(amount -> amount < config.minEcoNotify())
                .orElse(false);
    }
}
