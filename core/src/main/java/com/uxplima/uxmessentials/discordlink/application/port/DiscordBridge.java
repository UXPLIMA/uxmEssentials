package com.uxplima.uxmessentials.discordlink.application.port;

/**
 * Tells the discord-link use cases whether the optional Discord bridge is available to redeem a link code right
 * now: installed, connected, and linking-capable. A link code is only useful if something on the Discord side
 * can redeem it, so the issuing paths consult this before minting one and decline when it answers {@code false}.
 *
 * <p>An outbound adapter implements this over the platform's service registry (the bridge advertises its
 * presence there once connected); {@code :core} stays free of any platform dependency by depending only on this
 * port. The default deployment ships no bridge, so the honest answer there is {@code false}.
 */
public interface DiscordBridge {

    /** Whether the Discord bridge is present and ready to redeem a link code at this moment. */
    boolean available();
}
