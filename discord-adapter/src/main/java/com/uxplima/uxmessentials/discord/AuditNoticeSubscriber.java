package com.uxplima.uxmessentials.discord;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subscribes to the host plugin's {@link NotificationSource} and drives the forwarding pipeline: for every host
 * {@link AuditNotice} it formats the line ({@link NotificationFormatter}), sheds load under a flood
 * ({@link NotificationRateLimiter}), and hands the survivor to the {@link NotificationForwarder}, which applies
 * the per-category channel mapping and enable flags from {@code discord.conf}. This is the "subscribes to the
 * notification sources … and forwards a formatted message to the mapped Discord channel" step.
 *
 * <h2>Lifecycle</h2>
 * {@link #start()} opens the subscription (the bridge calls it once the gateway is connected); {@link #stop()}
 * closes it and is idempotent, so a disable that races the async connect never leaves a dangling listener. Both
 * are guarded by an {@link AtomicReference} holding the live {@link NotificationSource.Subscription}.
 *
 * <h2>Loop sentinel and ordering</h2>
 * The loop guard is applied first inside the formatter: a bridge-originated notice
 * ({@code originServer = "discord"}) is dropped before the rate limiter even sees it, so a mirrored action can
 * never consume budget or be forwarded twice (docs/09-deployment.md Path C). A notice dropped by the loop guard
 * or a disabled/unmapped category is therefore <em>not</em> counted against the flood budget.
 */
public final class AuditNoticeSubscriber {

    private final NotificationSource source;
    private final NotificationForwarder forwarder;
    private final NotificationRateLimiter rateLimiter;
    private final AtomicReference<NotificationSource.Subscription> subscription = new AtomicReference<>();

    public AuditNoticeSubscriber(
            NotificationSource source, NotificationForwarder forwarder, NotificationRateLimiter rateLimiter) {
        this.source = Objects.requireNonNull(source, "source");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
    }

    /** Begin receiving host notifications. A second call while already started is a no-op. */
    public void start() {
        subscription.updateAndGet(existing -> existing != null ? existing : source.subscribe(this::handle));
    }

    /** Stop receiving and release the subscription. Safe to call when never started. */
    public void stop() {
        NotificationSource.Subscription open = subscription.getAndSet(null);
        if (open != null) {
            open.close();
        }
    }

    /**
     * Process one host notice: format (dropping bridge-originated loops), throttle, forward. Returns whether the
     * notice reached the gateway, which makes the pipeline unit-assertable end to end.
     */
    boolean handle(AuditNotice notice) {
        Objects.requireNonNull(notice, "notice");
        Optional<Notification> formatted = NotificationFormatter.format(notice);
        if (formatted.isEmpty() || !rateLimiter.tryAcquire()) {
            return false;
        }
        return forwarder.forward(formatted.get());
    }
}
