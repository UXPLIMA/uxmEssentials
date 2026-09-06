package com.uxplima.uxmessentials.shared.adapter.outbound.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link DomainEventPublisher} implementation: an in-process bus that fans each published event
 * out to registered subscribers. A context's outbound adapter subscribes to bridge the event to a
 * Bukkit event for other plugins, or to react in-process (the presence context clears AFK on a
 * teleport); the publisher itself stays free of any concrete event type, accepting the cross-cutting
 * {@link DomainEvent} marker.
 *
 * <p>A failing subscriber is logged and skipped so one bad listener never blocks delivery to the
 * others: the publish call is not a transaction across subscribers.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code subscribers} is a {@link CopyOnWriteArrayList}:
 * subscription is rare, dispatch is read-heavy and lock-free, and a publish iterates a stable
 * snapshot.
 */
@NullMarked
public final class InProcessDomainEventPublisher implements DomainEventPublisher {

    private final Logger log;
    private final List<Consumer<DomainEvent>> subscribers = new CopyOnWriteArrayList<>();

    public InProcessDomainEventPublisher(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Register a subscriber notified of every subsequent published event. */
    public void subscribe(Consumer<DomainEvent> subscriber) {
        subscribers.add(Objects.requireNonNull(subscriber, "subscriber"));
    }

    /**
     * Remove a previously {@link #subscribe(Consumer) subscribed} consumer so a module that subscribed during
     * wiring can drop its listener on stop. Without this a per-reload subscribe would leak a stale listener that
     * keeps firing against torn-down state. Removal is by identity, so the caller must hold the same instance.
     */
    public void unsubscribe(Consumer<DomainEvent> subscriber) {
        subscribers.remove(Objects.requireNonNull(subscriber, "subscriber"));
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<DomainEvent> subscriber : subscribers) {
            dispatch(subscriber, event);
        }
    }

    private void dispatch(Consumer<DomainEvent> subscriber, DomainEvent event) {
        try {
            subscriber.accept(event);
        } catch (RuntimeException failure) {
            log.error("domain-event subscriber failed for " + event.getClass().getSimpleName(), failure);
        }
    }
}
