package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.playerstate.domain.event.FlyToggled;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /fly [player]}: flip a player's flight allowance. The plain on/off toggle (timed fly is deferred
 * post-v1). The snapshot is mutated atomically through the {@link PlayerStateStore}, the new value is pushed
 * to the live player by the {@link StateReconciler} on the owning region thread (which sets
 * {@code setAllowFlight} and, on disable, lowers the player out of flight), the {@link FlyToggled} event is
 * published, and the actor and subject are notified.
 */
public final class ToggleFly {

    private final PlayerStateStore store;
    private final StateReconciler reconciler;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public ToggleFly(
            PlayerStateStore store,
            StateReconciler reconciler,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Toggle {@code who}'s own flight allowance. */
    public boolean toggle(PlayerRef who) {
        return toggleFor(who, who);
    }

    /** Toggle {@code subject}'s flight allowance on behalf of {@code actor}. */
    public boolean toggleFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        PlayerStateSnapshot updated = store.update(subject, PlayerStateSnapshot::toggleFly);
        reconciler.reconcile(subject, updated);
        events.publish(new FlyToggled(subject, actor, updated.fly(), clock.instant()));
        notify(actor, subject, updated.fly());
        return updated.fly();
    }

    private void notify(PlayerRef actor, PlayerRef subject, boolean enabled) {
        if (actor.equals(subject)) {
            notifier.send(actor, enabled ? PlayerstateMessageKey.FLY_ON : PlayerstateMessageKey.FLY_OFF);
            return;
        }
        Map<String, String> player = Map.of("player", subject.name());
        notifier.send(
                actor, enabled ? PlayerstateMessageKey.FLY_OTHER_ON : PlayerstateMessageKey.FLY_OTHER_OFF, player);
        notifier.send(subject, enabled ? PlayerstateMessageKey.FLY_ON : PlayerstateMessageKey.FLY_OFF);
    }
}
