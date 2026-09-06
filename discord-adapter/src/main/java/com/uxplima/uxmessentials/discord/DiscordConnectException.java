package com.uxplima.uxmessentials.discord;

/**
 * Raised when the {@link DiscordGateway} cannot establish its connection. A bad token, an interrupted login,
 * or a client build failure. The bootstrap catches it, logs the reason on the bridge's plugin logger, and
 * self-disables (docs/09-deployment.md Path C: "self-disables on token failure … and logs the reason").
 */
public final class DiscordConnectException extends Exception {

    public DiscordConnectException(String message, Throwable cause) {
        super(message, cause);
    }

    public DiscordConnectException(String message) {
        super(message);
    }
}
