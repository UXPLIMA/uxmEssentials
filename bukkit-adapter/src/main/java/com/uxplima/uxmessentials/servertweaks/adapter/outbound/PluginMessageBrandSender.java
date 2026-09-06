package com.uxplima.uxmessentials.servertweaks.adapter.outbound;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import org.jspecify.annotations.NullMarked;

/**
 * Sends the configured server brand to a player over the {@code minecraft:brand} plugin-message channel, so the
 * client's F3 debug screen shows it instead of the default "Paper" brand. The payload is encoded once at construction
 * ({@link BrandPayload}) and reused for every join, and the channel itself is registered and unregistered by the
 * module wiring: this sender only writes to it.
 *
 * <p>The channel is the reserved {@code minecraft:brand} custom-payload channel: re-sending the brand after the player
 * has joined overrides the brand the server announced during the login/configuration phase. The client renders the
 * value as plain text, so the brand string is passed through verbatim.
 */
@NullMarked
public final class PluginMessageBrandSender implements ServerBrandSender {

    /** The reserved vanilla channel that carries the server brand shown on F3. */
    public static final String BRAND_CHANNEL = "minecraft:brand";

    private final Plugin plugin;
    private final byte[] payload;

    public PluginMessageBrandSender(Plugin plugin, String brand) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.payload = BrandPayload.encode(Objects.requireNonNull(brand, "brand"));
    }

    @Override
    public void send(Player player) {
        Objects.requireNonNull(player, "player");
        player.sendPluginMessage(plugin, BRAND_CHANNEL, payload.clone());
    }
}
