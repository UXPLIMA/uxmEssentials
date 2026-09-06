package com.uxplima.uxmessentials.discord;

/**
 * The host plugin's notification feed, consumed by the bridge through Bukkit's {@code ServicesManager}: there
 * is no compile-time link to {@code :bukkit-adapter} (docs/01-architecture.md, docs/09-deployment.md Path C).
 * The host registers an implementation that fans audit events and economy notifications (mute/jail/tempban/
 * banip, eco-admin, etc.) out as {@link AuditNotice}s; the bridge subscribes once the gateway is ready and
 * unsubscribes on disable.
 *
 * <p>Keeping the subscription behind this thin port is what lets the forwarding pipeline be unit-tested with a
 * fake source and a fake gateway, with no live JDA connection and no MockBukkit server (CLAUDE.md GROUND RULE).
 */
public interface NotificationSource {

    /** A registered consumer of host notifications. */
    @FunctionalInterface
    interface Listener {
        /** Called once per host notification, on the host's emitting thread (never the bridge's concern). */
        void onNotice(AuditNotice notice);
    }

    /**
     * Register a listener for host notifications. Returns a {@link Subscription} the caller closes to stop
     * receiving; registering the same listener twice is the caller's responsibility to avoid.
     */
    Subscription subscribe(Listener listener);

    /** Handle to a live subscription; closing it stops delivery. Idempotent. */
    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
