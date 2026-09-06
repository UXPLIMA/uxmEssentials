package com.uxplima.uxmessentials.discord;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;

/**
 * Thin abstraction over the Discord client. The bridge talks to Discord exclusively through this port so the
 * notification forwarder can be unit-tested against a fake implementation with no live JDA connection (a real
 * gateway login is untestable in a build environment, CLAUDE.md "tests use a fake gateway").
 *
 * <p>Every method is expected to run <em>off</em> the server's main thread: {@link #connect(String)} performs
 * a blocking login and {@link #sendToChannel(String, String)} enqueues an asynchronous REST call, neither of
 * which may stall a tick. The {@link com.uxplima.uxmessentials.discord.JdaDiscordGateway JDA implementation}
 * keeps that contract; callers schedule invocations off-tick.
 */
public interface DiscordGateway {

    /**
     * Establish the Discord connection with the given bot token and block until the client is ready. Called
     * once on plugin enable, only when a token is configured. Throws if the login fails so the caller can
     * self-disable and log the reason rather than run half-connected.
     *
     * @throws DiscordConnectException if the client cannot be built or never reaches a ready state
     */
    void connect(String token) throws DiscordConnectException;

    /**
     * Post a plain-text message to the Discord channel with the given id. A no-op when the gateway is not
     * connected or the channel id is unknown, so a misconfigured channel never throws on the notification
     * path. The send itself is asynchronous: this method returns without waiting for the REST round-trip.
     */
    void sendToChannel(String channelId, String message);

    /**
     * Register the inbound {@code /link} slash command and a handler that redeems a code through the given
     * host confirmation seam. Called once after {@link #connect(String)} succeeds, only when the host exposes a
     * {@link DiscordLinkConfirmation} via the {@code ServicesManager}; the outbound send path is untouched and
     * needs no extra gateway intent (slash interactions arrive regardless of the message intents). A default
     * no-op keeps the contract testable against a fake gateway that has no live JDA to register a command on.
     */
    default void enableLinking(DiscordLinkConfirmation confirmation) {
        // No-op by default: a fake gateway records the call without a live JDA; the JDA impl overrides this.
    }

    /** Whether the gateway currently holds a ready connection. */
    boolean isConnected();

    /** Tear the connection down cleanly on plugin disable. Idempotent: safe to call when never connected. */
    void shutdown();
}
